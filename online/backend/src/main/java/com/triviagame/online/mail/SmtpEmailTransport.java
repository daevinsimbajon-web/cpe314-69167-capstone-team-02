package com.triviagame.online.mail;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.*;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "smtp")
public class SmtpEmailTransport implements EmailTransport {
  private final JavaMailSender sender;
  private final EmailTemplates templates;
  private final String from, name, user, password, host;
  private final boolean tls, auth, capture, test;

  public SmtpEmailTransport(
      JavaMailSender sender,
      EmailTemplates templates,
      Environment env,
      @Value("${app.mail.from-address:}") String from,
      @Value("${app.mail.from-name:Hootly}") String name,
      @Value("${spring.mail.username:}") String user,
      @Value("${spring.mail.password:}") String password,
      @Value("${spring.mail.host:smtp.gmail.com}") String host,
      @Value("${SMTP_STARTTLS_ENABLED:true}") boolean tls,
      @Value("${SMTP_AUTH_ENABLED:true}") boolean auth) {
    this.sender = sender;
    this.templates = templates;
    this.from = from;
    this.name = name;
    this.user = user;
    this.password = password;
    this.host = host;
    this.tls = tls;
    this.auth = auth;
    this.test =
        Arrays.stream(env.getActiveProfiles())
            .anyMatch(profile -> profile.equals("test") || profile.equals("smtp-capture"));
    this.capture =
        Arrays.asList(env.getActiveProfiles()).contains("smtp-capture")
            && Arrays.asList("localhost", "127.0.0.1", "::1").contains(host);
  }

  @Override
  public boolean configured() {
    return (!test || capture)
        && !from.isBlank()
        && !from.contains("\r")
        && !from.contains("\n")
        && !name.contains("\r")
        && !name.contains("\n")
        && (capture || tls && auth && !user.isBlank() && !password.isBlank());
  }

  @Override
  public void send(EmailMessage message)
      throws java.io.UnsupportedEncodingException, jakarta.mail.MessagingException {
    if (!configured()) throw new IllegalStateException("Mail is not configured");
    var content = templates.render(message);
    var mime = sender.createMimeMessage();
    var helper = new MimeMessageHelper(mime, true, "UTF-8");
    helper.setFrom(from, name);
    helper.setTo(message.to());
    helper.setSubject(content.subject());
    helper.setText(content.text(), content.html());
    helper.addInline("hootly-owl", new ClassPathResource("mail/hootly-owl.png"), "image/png");
    sender.send(mime);
  }
}
