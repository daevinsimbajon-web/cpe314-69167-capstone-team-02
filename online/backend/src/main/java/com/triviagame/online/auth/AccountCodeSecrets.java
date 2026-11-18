package com.triviagame.online.auth;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AccountCodeSecrets {
    private final byte[] key;private final SecureRandom random=new SecureRandom();
    public AccountCodeSecrets(@Value("${app.otp-secret:}") String secret){
        if(secret.isBlank()){key=null;return;}
        try{key=Base64.getDecoder().decode(secret);if(key.length<32)throw new IllegalArgumentException();}
        catch(IllegalArgumentException invalid){throw new IllegalStateException("OTP_HMAC_SECRET must be a private Base64 value containing at least 32 random bytes");}
    }
    public boolean configured(){return key!=null;}
    public String code(){return String.format(Locale.ROOT,"%06d",random.nextInt(1_000_000));}
    public String token(){byte[] bytes=new byte[32];random.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    public String digest(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception invalid){throw new IllegalStateException(invalid);}}
    public String codeDigest(UUID challenge,UUID user,String purpose,String code){
        if(key==null)throw new IllegalStateException("Email verification is not configured");
        try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal((challenge+"\n"+user+"\n"+purpose+"\n"+code).getBytes(StandardCharsets.UTF_8)));}catch(Exception invalid){throw new IllegalStateException(invalid);}
    }
    public boolean matches(String expected,String actual){return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),actual.getBytes(StandardCharsets.UTF_8));}
}
