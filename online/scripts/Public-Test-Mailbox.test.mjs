import test from 'node:test';
import assert from 'node:assert/strict';
import {verificationMailCode} from './Public-Test-Mailbox.mjs';

test('reads a real JavaMail-style nested MIME code without using HTML or another purpose',()=>{
  const subject=Buffer.from('Welcome to Hootly — Verify your email').toString('base64');
  const text=Buffer.from('Your verification code:\r\n\r\n123456\r\nExpires in 10 minutes.').toString('base64');
  const mail=[`Subject: =?UTF-8?B?${subject}?=`,'Content-Type: multipart/mixed; boundary="outer"','',
    '--outer','Content-Type: multipart/related; boundary="related"','',
    '--related','Content-Type: multipart/alternative; boundary="inner"','',
    '--inner','Content-Type: text/plain; charset=UTF-8','Content-Transfer-Encoding: base64','',text,
    '--inner','Content-Type: text/html','', '<p>999999</p>','--inner--','--related--','--outer--'].join('\r\n');
  assert.equal(verificationMailCode(mail,'verification'),'123456');
  assert.equal(verificationMailCode(mail,'reset'),null);
});
