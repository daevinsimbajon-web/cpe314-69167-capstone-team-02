function quotedPrintable(value){
  return Buffer.from(value.replace(/=\r?\n/g,'').replace(/=([a-f0-9]{2})/gi,
    (_,hex)=>String.fromCharCode(parseInt(hex,16))),'latin1').toString('utf8');
}
function subject(message){
  return (message.match(/^Subject: ([\s\S]*?)(?=\r\n[^ \t])/mi)?.[1]??'')
    .replace(/\r\n[ \t]+/g,' ')
    .replace(/=\?UTF-8\?([BQ])\?([^?]+)\?=/gi,(_,encoding,value)=>
      encoding.toUpperCase()==='B'?Buffer.from(value,'base64').toString('utf8'):
        quotedPrintable(value.replace(/_/g,' ')));
}
function plainCode(message){
  // Split every multipart level: JavaMail nests alternative parts inside related/mixed parts.
  const parts=message.split(/\r?\n--[^\r\n]+(?:\r?\n|$)/);
  for(const part of parts){
    if(!/Content-Type:\s*text\/plain/i.test(part))continue;
    const separator=part.indexOf('\r\n\r\n');if(separator<0)continue;
    const headers=part.slice(0,separator),body=part.slice(separator+4);
    const decoded=/Content-Transfer-Encoding:\s*base64/i.test(headers)
      ?Buffer.from(body.replace(/\s/g,''),'base64').toString('utf8')
      :/Content-Transfer-Encoding:\s*quoted-printable/i.test(headers)?quotedPrintable(body):body;
    const code=decoded.match(/(?:^|\r?\n)(\d{6})(?:\r?\n|$)/)?.[1];
    if(code)return code;
  }
  return null;
}
export function verificationMailCode(message,purpose){
  const expected=purpose==='verification'?/verify.*email/i:/reset.*password/i;
  return expected.test(subject(message))?plainCode(message):null;
}

/** Live mailbox automation is disabled. Use smtp-capture.mjs for isolated tests. */
export class PublicTestMailbox {
 static async connect(){throw new Error("Live Gmail test automation is disabled. Use Test-PersonalQuizzes.ps1 with the isolated SMTP sink.");}
}
