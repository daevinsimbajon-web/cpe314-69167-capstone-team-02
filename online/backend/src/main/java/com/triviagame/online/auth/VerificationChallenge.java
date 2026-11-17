package com.triviagame.online.auth;
import java.time.Instant;
public record VerificationChallenge(String ticket,String maskedEmail,Instant expiresAt,Instant resendAt,String purpose,String deliveryStatus) { }
