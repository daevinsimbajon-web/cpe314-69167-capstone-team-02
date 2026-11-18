package com.triviagame.online.mail;

import jakarta.annotation.PreDestroy;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.*;
import org.slf4j.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.*;

@Service
public class EmailDelivery {
    private static final Logger LOG=LoggerFactory.getLogger(EmailDelivery.class);
    private final EmailTransport transport;private final JdbcTemplate jdbc;private final Clock clock;
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64),task->{Thread t=new Thread(task,"hootly-email");t.setDaemon(true);return t;});
    public EmailDelivery(EmailTransport transport,JdbcTemplate jdbc,Clock clock){this.transport=transport;this.jdbc=jdbc;this.clock=clock;}
    public boolean configured(){return transport.configured();}
    public void queue(UUID user,UUID challenge,String address,String name,EmailKind kind,String code){
        UUID id=UUID.randomUUID();var now=clock.instant();
        jdbc.update("INSERT INTO account_email_deliveries(id,user_id,challenge_id,kind,status,created_at,updated_at,provider) VALUES(?,?,?,?,'QUEUED',?,?,?)",id,user,challenge,kind.name(),at(now),at(now),transport.provider());
        var message=new EmailMessage(id,address,name,kind,code,now);
        Runnable submit=()->{try{workers.execute(()->send(message,challenge));}catch(RejectedExecutionException full){failed(id,challenge,"FAILED","queue_full");}};
        if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){submit.run();}});else submit.run();
    }
    private void send(EmailMessage message,UUID challenge){
        status(message.id(),challenge,"SENDING");
        DeliveryReceipt receipt;
        try{receipt=transport.sendTracked(message);}
        catch(MailDeliveryException failure){failed(message.id(),challenge,failure.uncertain()?"UNKNOWN":"FAILED",failure.category());LOG.warn("Hootly email delivery problem: kind={}, delivery={}, category={}",message.kind(),message.id(),failure.category());return;}
        catch(Exception failure){failed(message.id(),challenge,"FAILED","transport_error");LOG.warn("Hootly email delivery failed: kind={}, delivery={}",message.kind(),message.id());return;}
        try{jdbc.update("UPDATE account_email_deliveries SET provider=?,provider_message_id=? WHERE id=?",receipt.provider(),receipt.messageId(),message.id());status(message.id(),challenge,receipt.status());LOG.info("Hootly email accepted: provider={}, kind={}, delivery={}, providerMessageId={}, status={}",receipt.provider(),message.kind(),message.id(),receipt.messageId(),receipt.status());}
        catch(Exception failure){LOG.warn("Hootly email acceptance could not be recorded: kind={}, delivery={}",message.kind(),message.id());}
    }
    private void failed(UUID id,UUID challenge,String state,String category){jdbc.update("UPDATE account_email_deliveries SET failure_category=? WHERE id=?",category,id);status(id,challenge,state);}
    private void status(UUID id,UUID challenge,String state){jdbc.update("UPDATE account_email_deliveries SET status=?,updated_at=? WHERE id=?",state,at(clock.instant()),id);if(challenge!=null)jdbc.update("UPDATE account_email_challenges SET delivery_status=? WHERE id=?",state,challenge);}
    @EventListener(ApplicationReadyEvent.class) public void recover(){jdbc.update("UPDATE account_email_challenges SET delivery_status='UNKNOWN' WHERE delivery_status='SENDING'");jdbc.update("UPDATE account_email_challenges SET delivery_status='FAILED' WHERE delivery_status='QUEUED'");jdbc.update("UPDATE account_email_deliveries SET status='UNKNOWN',failure_category='restart_uncertain' WHERE status='SENDING'");jdbc.update("UPDATE account_email_deliveries SET status='FAILED',failure_category='restart_queued' WHERE status='QUEUED'");LOG.info("Hootly email transport active: provider={}, configured={}",transport.provider(),transport.configured());}
    private OffsetDateTime at(Instant value){return value.atOffset(ZoneOffset.UTC);}
    @PreDestroy public void close(){workers.shutdownNow();}
}
