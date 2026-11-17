package com.triviagame.online.mail;

/** Acceptance by a provider is not proof of inbox delivery. */
public record DeliveryReceipt(String provider,String messageId,String status) {}
