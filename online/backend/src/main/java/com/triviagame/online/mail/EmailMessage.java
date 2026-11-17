package com.triviagame.online.mail;

import java.time.Instant;
import java.util.UUID;

/** Ephemeral transport data. Never persist or log code-bearing message bodies. */
public record EmailMessage(UUID id,String to,String displayName,EmailKind kind,String code,Instant occurredAt) {
    @Override public String toString(){return "Hootly email ["+kind+", "+id+"]";}
}
