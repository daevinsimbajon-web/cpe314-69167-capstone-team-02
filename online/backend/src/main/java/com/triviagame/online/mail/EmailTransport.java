package com.triviagame.online.mail;

public interface EmailTransport {
    boolean configured();
    default String provider(){return "smtp";}
    void send(EmailMessage message) throws Exception;
    default DeliveryReceipt sendTracked(EmailMessage message) throws Exception {send(message);return new DeliveryReceipt("smtp",null,"SENT");}
}
