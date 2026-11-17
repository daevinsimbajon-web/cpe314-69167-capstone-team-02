package com.triviagame.online.mail;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class EmailTemplates {
    public record Rendered(String subject,String text,String html) { }
    public Rendered render(EmailMessage message) {
        boolean verify=message.kind()==EmailKind.EMAIL_VERIFICATION,changed=message.kind()==EmailKind.PASSWORD_CHANGED;
        String subject=changed?"Hootly — Your password has changed":verify?"Welcome to Hootly — Verify your email":"Hootly — Reset your password";
        String title=changed?"Your password has changed":verify?"Welcome to Hootly!":"Forgot your password?";
        String body=changed?"Your account password has been updated. If you made this change, no further action is needed. If you did not make this change, secure your account immediately using Hootly’s password recovery feature.":verify?"You’re one little step away from joining the flock. Use the verification code below to activate your account.":"Even the wisest owls forget sometimes. Use the verification code below to securely reset your Hootly password.";
        String when=DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm 'UTC'").withZone(ZoneOffset.UTC).format(message.occurredAt());
        String code=changed?"":message.code();
        String text="Hootly — A little wiser. A lot closer.\n\n"+"Hello "+(message.displayName()==null?"friend":message.displayName())+",\n\n"+title+"\n\n"+body+"\n\n"+(changed?"Changed: "+when:code+"\nExpires in 10 minutes. Enter this code in Hootly. Never share this code with anyone.\nIf you didn’t request this code, you can safely ignore this email.")+"\n\n"+(changed?"Stay safe under the stars.":"See you under the stars.");
        String panel=changed?"<p style=\"color:#C7A9FF;font-size:14px;line-height:24px\">Changed: "+escape(when)+"</p>":"<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\"><tr><td align=\"center\" style=\"padding:22px 10px;background:#25213F;border:1px solid #8959DA;border-radius:14px\"><p style=\"margin:0 0 12px;color:#C7A9FF;font-size:11px;letter-spacing:2px\">YOUR "+(verify?"VERIFICATION":"RESET")+" CODE</p><p style=\"margin:0;color:#F4EDFF;font-family:Consolas,monospace;font-size:32px;font-weight:bold;letter-spacing:8px\">"+escape(code)+"</p><p style=\"margin:12px 0 0;color:#B6B0CC;font-size:12px\">Expires in 10 minutes</p></td></tr></table>";
        try {
            String template=new ClassPathResource("mail/security-email.html").getContentAsString(StandardCharsets.UTF_8);
            return new Rendered(subject,text,template.replace("{{title}}",escape(title)).replace("{{body}}",escape(body)).replace("{{name}}",escape(message.displayName()==null?"friend":message.displayName())).replace("{{panel}}",panel).replace("{{guidance}}",changed?"If this wasn’t you, open Hootly and choose Forgot password? to secure your account.":"Enter this code in Hootly. Didn’t request it? You can safely ignore this email. Never share your verification code with anyone."));
        }catch(Exception failure){throw new IllegalStateException("Email template is unavailable",failure);}
    }
    private static String escape(String value){return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
}
