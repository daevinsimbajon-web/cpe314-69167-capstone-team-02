package com.triviagame.online.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.io.IOException;
import jakarta.validation.constraints.*;
import java.util.UUID;

/** Bounded illustration parameters, never markup or a client supplied URL/identity. */
public record AvatarConfig(
        @NotNull @Min(1) @Max(1) Integer version,
        @NotNull @Min(0) @Max(8) Integer body,
        @NotNull @Min(0) @Max(3) Integer eyes,
        @NotNull @Min(0) @Max(5) Integer iris,
        @NotNull @Min(0) @Max(2) Integer wings,
        @NotNull @Min(0) @Max(3) Integer marking,
        @NotNull @Min(0) @Max(7) Integer accessory,
        @NotNull @Min(0) @Max(5) Integer expression,
        @JsonInclude(JsonInclude.Include.NON_NULL) @JsonDeserialize(using=SpeciesNumber.class) @Min(0) @Max(7) Integer species) {
    public static class SpeciesNumber extends JsonDeserializer<Integer> {
        @Override public Integer deserialize(JsonParser parser,DeserializationContext context) throws IOException {
            if(parser.currentToken()!=JsonToken.VALUE_NUMBER_INT)throw InvalidFormatException.from(parser,"Species must be a whole number",parser.getText(),Integer.class);
            return parser.getIntValue();
        }
    }
    private static final ObjectMapper JSON=new ObjectMapper();
    public static AvatarConfig forUser(UUID user) {
        int[] state={user.toString().hashCode()};if(state[0]==0)state[0]=0x6d2b79f5;
        return new AvatarConfig(1,next(state,9),next(state,4),next(state,6),next(state,3),next(state,4),next(state,8),next(state,6),null);
    }
    /** Existing configurations stay untouched; only newly created identities receive a species. */
    public static AvatarConfig forNewUser(UUID user) {
        var base=forUser(user);
        return new AvatarConfig(base.version,base.body,base.eyes,base.iris,base.wings,base.marking,base.accessory,base.expression,Integer.remainderUnsigned(user.hashCode(),8));
    }
    private static int next(int[] state,int bound){int x=state[0];x^=x<<13;x^=x>>>17;x^=x<<5;state[0]=x;return Integer.remainderUnsigned(x,bound);}
    public boolean valid(){return version!=null&&version==1&&within(body,8)&&within(eyes,3)&&within(iris,5)&&within(wings,2)&&within(marking,3)&&within(accessory,7)&&within(expression,5)&&(species==null||within(species,7));}
    private static boolean within(Integer n,int max){return n!=null&&n>=0&&n<=max;}
    public String stored(){try{return JSON.writeValueAsString(this);}catch(Exception invalid){throw new IllegalArgumentException("Invalid avatar configuration");}}
    public static AvatarConfig read(String stored,UUID user){
        if(stored!=null)try{AvatarConfig avatar=JSON.readValue(stored,AvatarConfig.class);if(avatar!=null&&avatar.valid())return avatar;}catch(Exception ignored){/* Safe deterministic legacy fallback. */}
        return forUser(user);
    }
}
