import {expect,it,vi} from 'vitest';
import type {ClientRequest,IncomingMessage} from 'node:http';
import {forwarding,proxyHeaders,publicOrigins} from '../../devProxy';

const publicOrigin='https://approved-tunnel.example';
function request(host:string,peer='127.0.0.1',headers:Record<string,string>={}):IncomingMessage{
  return {headers:{host,...headers},socket:{remoteAddress:peer}} as IncomingMessage;
}
it('uses exact HTTPS host approval and refuses wildcard or credential-bearing origins',()=>{
  expect(publicOrigins(`${publicOrigin},${publicOrigin}/`)).toEqual([publicOrigin]);
  for(const value of ['https://*.example','https://.example','http://approved-tunnel.example','https://user:secret@example.test','https://example.test/path']){
    expect(()=>publicOrigins(value)).toThrow();
  }
});
it('recognizes the existing local Cloudflare hop and preserves localhost HTTP',()=>{
  expect(forwarding(request('approved-tunnel.example','::1',{
    'x-forwarded-proto':'https','cf-connecting-ip':'203.0.113.42',
  }),[publicOrigin])).toEqual({host:'approved-tunnel.example',protocol:'https',port:'443',client:'203.0.113.42'});
  expect(forwarding(request('localhost:5173'),[publicOrigin]))
    .toEqual({host:'localhost:5173',protocol:'http',port:'5173',client:'127.0.0.1'});
});
it('does not trust spoofed forwarded values from a LAN client or unapproved host',()=>{
  const forged={'x-forwarded-proto':'https','cf-connecting-ip':'203.0.113.42','x-forwarded-for':'198.51.100.99'};
  expect(forwarding(request('approved-tunnel.example','192.168.1.10',forged),[publicOrigin]))
    .toMatchObject({protocol:'http',client:'192.168.1.10'});
  expect(forwarding(request('unapproved.example','127.0.0.1',forged),[publicOrigin]))
    .toMatchObject({protocol:'http',client:'127.0.0.1'});
  const outgoing={removeHeader:vi.fn(),setHeader:vi.fn()} as unknown as ClientRequest;
  proxyHeaders(outgoing,request('localhost:5173','127.0.0.1',forged),[publicOrigin]);
  expect(outgoing.removeHeader).toHaveBeenCalledWith('forwarded');
  expect(outgoing.setHeader).toHaveBeenCalledWith('X-Forwarded-Proto','http');
  expect(outgoing.setHeader).toHaveBeenCalledWith('X-Forwarded-For','127.0.0.1');
});
