package com.triviagame.online;

import com.triviagame.online.mail.*;
import java.util.concurrent.CopyOnWriteArrayList;

/** Test-only in-memory mailbox. Never included in the production artifact. */
public class CapturedMailbox implements EmailTransport {
    final CopyOnWriteArrayList<EmailMessage> messages=new CopyOnWriteArrayList<>();
    volatile boolean fail;
    @Override public boolean configured(){return true;}
    @Override public void send(EmailMessage message){if(fail)throw new IllegalStateException("Simulated SMTP rejection");messages.add(message);}
    EmailMessage await(String address,EmailKind kind) throws InterruptedException {
        long deadline=System.nanoTime()+java.time.Duration.ofSeconds(5).toNanos();
        while(System.nanoTime()<deadline){var found=messages.stream().filter(m->m.to().equals(address)&&m.kind()==kind).reduce((a,b)->b);if(found.isPresent())return found.get();Thread.sleep(20);}
        throw new AssertionError("No captured test message for "+kind);
    }
}
