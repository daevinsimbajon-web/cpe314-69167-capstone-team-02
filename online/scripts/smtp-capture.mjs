// Local test SMTP sink only. Never forwards or sends mail over the Internet.
import net from 'node:net';
import http from 'node:http';
const key=process.env.HOOTLY_CAPTURE_KEY;
if(!key||key.length<32)throw Error('A random capture key is required.');
const messages=[];
net.createServer(socket=>{
  socket.setEncoding('utf8');socket.write('220 Hootly isolated mailbox ESMTP\r\n');
  let buffer='',data=false,body=[];
  socket.on('error',()=>{});
  socket.on('data',chunk=>{buffer+=chunk;let index;while((index=buffer.indexOf('\r\n'))>=0){const line=buffer.slice(0,index);buffer=buffer.slice(index+2);
    if(data){if(line==='.'){messages.push(body.join('\r\n'));if(messages.length>1000)messages.shift();body=[];data=false;socket.write('250 Captured locally\r\n');}else body.push(line.replace(/^\.\./,'.'));continue;}
    if(/^EHLO/i.test(line))socket.write('250-localhost\r\n250 8BITMIME\r\n');
    else if(/^HELO|^MAIL FROM|^RCPT TO|^RSET|^NOOP/i.test(line))socket.write('250 OK\r\n');
    else if(/^DATA/i.test(line)){data=true;socket.write('354 End with dot\r\n');}
    else if(/^QUIT/i.test(line)){socket.end('221 Bye\r\n');}
    else socket.write('502 Unsupported\r\n');
  }});
}).listen(2525,'127.0.0.1');
http.createServer((request,response)=>{
  if(request.headers.authorization!==`Bearer ${key}`){response.writeHead(403).end();return;}
  response.setHeader('Content-Type','application/json');response.setHeader('Cache-Control','no-store');response.end(JSON.stringify({messages}));
}).listen(8026,'127.0.0.1');
