package com.triviagame.online.mail;

public interface EmailTransport {
  boolean configured();

  default String provider() {
    return "smtp";
  }

  void send(EmailMessage message)
      throws MailDeliveryException, java.io.IOException, jakarta.mail.MessagingException;

  default DeliveryReceipt sendTracked(EmailMessage message)
      throws MailDeliveryException, java.io.IOException, jakarta.mail.MessagingException {
    send(message);
    return new DeliveryReceipt("smtp", null, "SENT");
  }
}
