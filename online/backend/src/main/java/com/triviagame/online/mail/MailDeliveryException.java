package com.triviagame.online.mail;

/** Contains only a safe classification, never credentials, provider response bodies or recipient data. */
public class MailDeliveryException extends Exception {
    private final boolean uncertain;
    private final String category;
    public MailDeliveryException(String category,boolean uncertain){super(category);this.category=category;this.uncertain=uncertain;}
    public boolean uncertain(){return uncertain;}
    public String category(){return category;}
}
