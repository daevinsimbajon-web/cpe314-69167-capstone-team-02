package com.triviagame.online.mail;

import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmailDeliveryTrackingTest {
    @Test void acceptanceStoresMessageIdWithoutClaimingInboxDelivery()throws Exception{
        var jdbc=mock(JdbcTemplate.class);var transport=mock(EmailTransport.class);
        when(transport.sendTracked(any())).thenReturn(new DeliveryReceipt("mailjet","123456","ACCEPTED"));
        var delivery=new EmailDelivery(transport,jdbc,Clock.fixed(Instant.parse("2026-11-17T00:00:00Z"),ZoneOffset.UTC));
        try{
            delivery.queue(UUID.randomUUID(),null,"actual@example.test","Player",EmailKind.EMAIL_VERIFICATION,"483627");
            verify(jdbc,timeout(5000)).update(eq("UPDATE account_email_deliveries SET provider=?,provider_message_id=? WHERE id=?"),eq("mailjet"),eq("123456"),any(UUID.class));
            verify(jdbc,timeout(5000)).update(eq("UPDATE account_email_deliveries SET status=?,updated_at=? WHERE id=?"),eq("ACCEPTED"),any(),any(UUID.class));
        }finally{delivery.close();}
    }
    @Test void uncertainNetworkFailureIsNotReportedAsSent()throws Exception{
        var jdbc=mock(JdbcTemplate.class);var transport=mock(EmailTransport.class);
        when(transport.sendTracked(any())).thenThrow(new MailDeliveryException("network_uncertain",true));
        var delivery=new EmailDelivery(transport,jdbc,Clock.systemUTC());
        try{delivery.queue(UUID.randomUUID(),null,"actual@example.test","Player",EmailKind.PASSWORD_RESET,"483627");
            verify(jdbc,timeout(5000)).update(eq("UPDATE account_email_deliveries SET status=?,updated_at=? WHERE id=?"),eq("UNKNOWN"),any(),any(UUID.class));
        }finally{delivery.close();}
    }
    @Test void providerRejectionIsStoredAsSafeFailureCategory()throws Exception{
        var jdbc=mock(JdbcTemplate.class);var transport=mock(EmailTransport.class);
        when(transport.sendTracked(any())).thenThrow(new MailDeliveryException("sender_not_verified",false));
        var delivery=new EmailDelivery(transport,jdbc,Clock.systemUTC());
        try{
            delivery.queue(UUID.randomUUID(),null,"actual@example.test","Player",EmailKind.EMAIL_VERIFICATION,"483627");
            verify(jdbc,timeout(5000)).update(eq("UPDATE account_email_deliveries SET failure_category=? WHERE id=?"),eq("sender_not_verified"),any(UUID.class));
            verify(jdbc,timeout(5000)).update(eq("UPDATE account_email_deliveries SET status=?,updated_at=? WHERE id=?"),eq("FAILED"),any(),any(UUID.class));
        }finally{delivery.close();}
    }
}
