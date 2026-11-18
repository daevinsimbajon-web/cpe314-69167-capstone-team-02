import type {ClientRequest,IncomingMessage} from 'node:http';
import {isIP} from 'node:net';

/** Exact, operator-approved public origins. Never trust an entire tunnel-provider domain. */
export function publicOrigins(value:string):string[]{
  return [...new Set(value.split(',').map(item=>item.trim()).filter(Boolean).map(item=>{
    const url=new URL(item);
    if(url.protocol!=='https:'||url.username||url.password||url.pathname!=='/'||url.search||url.hash||url.hostname.includes('*')||url.hostname.startsWith('.')){
      throw new Error('HOOTLY_PUBLIC_ORIGINS must contain exact HTTPS origins.');
    }
    return url.origin;
  }))];
}

function header(request:IncomingMessage,name:string):string{
  const value=request.headers[name];return typeof value==='string'?value:'';
}
function loopback(address:string):boolean{
  return ['127.0.0.1','::1','::ffff:127.0.0.1'].includes(address);
}

/** Cloudflared is trusted only as a local peer on an explicitly approved public host. */
export function forwarding(request:IncomingMessage,approved:readonly string[]){
  const host=header(request,'host');
  const peer=request.socket.remoteAddress??'';
  const visitor=header(request,'cf-connecting-ip');
  const fromTunnel=loopback(peer)&&approved.includes(`https://${host.toLowerCase()}`)
    &&header(request,'x-forwarded-proto')==='https'&&isIP(visitor)!==0;
  const protocol=fromTunnel?'https':'http';
  const url=new URL(`${protocol}://${host}`);
  return {host:url.host,protocol,port:url.port||(protocol==='https'?'443':'80'),
    client:fromTunnel?visitor:peer||'127.0.0.1'};
}

/** Strip caller-supplied forwarding values before the backend's trusted-proxy boundary. */
export function proxyHeaders(proxy:ClientRequest,request:IncomingMessage,approved:readonly string[]){
  const values=forwarding(request,approved);
  proxy.removeHeader('forwarded');
  proxy.setHeader('X-Forwarded-Host',values.host);
  proxy.setHeader('X-Forwarded-Proto',values.protocol);
  proxy.setHeader('X-Forwarded-Port',values.port);
  proxy.setHeader('X-Forwarded-For',values.client);
}
