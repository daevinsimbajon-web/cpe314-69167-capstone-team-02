package com.triviagame.online.mail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "mailjet", matchIfMissing = true)
public class MailjetEmailTransport implements EmailTransport {
  private static final URI ENDPOINT = URI.create("https://api.mailjet.com/v3.1/send");
  private final EmailTemplates templates;
  private final ObjectMapper json;
  private final HttpClient client;
  private final String key, secret, from, name;
  private final boolean testProfile;

  @Autowired
  public MailjetEmailTransport(
      EmailTemplates templates,
      ObjectMapper json,
      Environment env,
      @Value("${app.mail.mailjet.api-key:}") String key,
      @Value("${app.mail.mailjet.secret-key:}") String secret,
      @Value("${app.mail.mailjet.from-email:}") String from,
      @Value("${app.mail.mailjet.from-name:Hootly}") String name) {
    this(
        templates,
        json,
        env,
        key,
        secret,
        from,
        name,
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build());
  }

  MailjetEmailTransport(
      EmailTemplates templates,
      ObjectMapper json,
      Environment env,
      String key,
      String secret,
      String from,
      String name,
      HttpClient client) {
    this.templates = templates;
    this.json = json;
    this.key = key;
    this.secret = secret;
    this.from = from;
    this.name = name;
    this.client = client;
    this.testProfile =
        Arrays.stream(env.getActiveProfiles())
            .anyMatch(p -> p.equals("test") || p.equals("smtp-capture"));
  }

  @Override
  public boolean configured() {
    return !testProfile
        && !key.isBlank()
        && !secret.isBlank()
        && !from.isBlank()
        && !from.contains("\r")
        && !from.contains("\n")
        && !name.contains("\r")
        && !name.contains("\n");
  }

  @Override
  public String provider() {
    return "mailjet";
  }

  @Override
  public void send(EmailMessage message) throws MailDeliveryException, IOException {
    sendTracked(message);
  }

  @Override
  public DeliveryReceipt sendTracked(EmailMessage message)
      throws MailDeliveryException, IOException {
    if (!configured()) throw new MailDeliveryException("not_configured", false);
    var content = templates.render(message);
    String image =
        Base64.getEncoder()
            .encodeToString(new ClassPathResource("mail/hootly-owl.png").getContentAsByteArray());
    var recipient = Map.of("Email", message.to());
    var inline =
        Map.of(
            "ContentType",
            "image/png",
            "Filename",
            "hootly-owl.png",
            "ContentID",
            "hootly-owl",
            "Base64Content",
            image);
    var payload =
        Map.of(
            "Messages",
            List.of(
                Map.of(
                    "From",
                    Map.of("Email", from, "Name", name),
                    "To",
                    List.of(recipient),
                    "Subject",
                    content.subject(),
                    "TextPart",
                    content.text(),
                    "HTMLPart",
                    content.html(),
                    "InlinedAttachments",
                    List.of(inline),
                    "CustomID",
                    message.id().toString())));
    String auth =
        Base64.getEncoder().encodeToString((key + ":" + secret).getBytes(StandardCharsets.UTF_8));
    var request =
        HttpRequest.newBuilder(ENDPOINT)
            .timeout(Duration.ofSeconds(12))
            .header("Authorization", "Basic " + auth)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(payload)))
            .build();
    HttpResponse<String> response;
    try {
      response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new MailDeliveryException("interrupted", true);
    } catch (IOException failure) {
      throw new MailDeliveryException("network_uncertain", true);
    }
    int status = response.statusCode();
    if (status < 200 || status >= 300) {
      String category =
          switch (status) {
            case 401 -> "authentication_rejected";
            case 403 -> "account_or_sender_rejected";
            case 429 -> "rate_limited";
            default ->
                status >= 500
                    ? "provider_unavailable"
                    : providerErrorCategory(response.body(), "request_rejected");
          };
      throw new MailDeliveryException(category, status >= 500);
    }
    JsonNode root;
    try {
      root = json.readTree(response.body());
    } catch (IOException failure) {
      throw new MailDeliveryException("invalid_response", true);
    }
    if (root == null) throw new MailDeliveryException("invalid_response", true);
    JsonNode messages = root.path("Messages");
    if (!messages.isArray() || messages.size() != 1)
      throw new MailDeliveryException("invalid_response", true);
    JsonNode result = messages.get(0);
    if (result.path("Status").asText().equalsIgnoreCase("error"))
      throw new MailDeliveryException(providerErrorCategory(result, "message_rejected"), false);
    if (!result.path("Status").asText().equalsIgnoreCase("success"))
      throw new MailDeliveryException("invalid_response", true);
    JsonNode recipients = result.path("To");
    if (!recipients.isArray()
        || recipients.size() != 1
        || !message.to().equals(recipients.path(0).path("Email").asText()))
      throw new MailDeliveryException("invalid_response", true);
    String id = recipients.path(0).path("MessageID").asText();
    if (!id.matches("[0-9]{1,40}")) throw new MailDeliveryException("invalid_response", true);
    return new DeliveryReceipt("mailjet", id, "ACCEPTED");
  }

  private String providerErrorCategory(String body, String fallback) {
    try {
      JsonNode root = json.readTree(body);
      JsonNode messages = root.path("Messages");
      return messages.isArray() && !messages.isEmpty()
          ? providerErrorCategory(messages.get(0), fallback)
          : providerErrorCategory(root, fallback);
    } catch (IOException malformed) {
      return fallback;
    }
  }

  private String providerErrorCategory(JsonNode result, String fallback) {
    JsonNode errors = result.path("Errors");
    if (!errors.isArray()) return fallback;
    for (JsonNode error : errors) {
      String message = error.path("ErrorMessage").asText("").toLowerCase(Locale.ROOT);
      if (message.contains("sender")
          && (message.contains("verif") || message.contains("valid") || message.contains("allow")))
        return "sender_not_verified";
      if (message.contains("auth") || message.contains("api key") || message.contains("credential"))
        return "authentication_rejected";
      if (message.contains("recipient")
          && (message.contains("invalid")
              || message.contains("block")
              || message.contains("allow"))) return "recipient_rejected";
    }
    return fallback;
  }
}
