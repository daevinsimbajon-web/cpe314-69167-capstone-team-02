package com.triviagame.online.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MailjetEmailTransportTest {
    @Configuration(proxyBeanMethods=false)
    @Import(MailjetEmailTransport.class)
    static class Transports {
        @Bean ObjectMapper mapper(){return new ObjectMapper();}
        @Bean EmailTemplates templates(){return new EmailTemplates();}
    }
    final ObjectMapper json=new ObjectMapper();
    final EmailMessage message=new EmailMessage(UUID.randomUUID(),"actual+tag@example.test","<Name & friend>",EmailKind.EMAIL_VERIFICATION,"483627",Instant.parse("2026-11-17T00:00:00Z"));
    MailjetEmailTransport transport(HttpClient client,StandardEnvironment env){return new MailjetEmailTransport(new EmailTemplates(),json,env,"key-placeholder","secret-placeholder","sender@example.test","Hootly",client);}
    @SuppressWarnings("unchecked") HttpResponse<String> response(int status,String body){HttpResponse<String> response=mock(HttpResponse.class);when(response.statusCode()).thenReturn(status);when(response.body()).thenReturn(body);return response;}
    void reply(HttpClient client,HttpResponse<String> response)throws Exception{when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(response);}
    String body(HttpRequest request)throws Exception{
        var output=new ByteArrayOutputStream();var done=new CompletableFuture<Void>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>(){
            public void onSubscribe(Flow.Subscription subscription){subscription.request(Long.MAX_VALUE);}
            public void onNext(ByteBuffer buffer){byte[] bytes=new byte[buffer.remaining()];buffer.get(bytes);output.writeBytes(bytes);}
            public void onError(Throwable error){done.completeExceptionally(error);}
            public void onComplete(){done.complete(null);}
        });done.get(5,TimeUnit.SECONDS);return output.toString(StandardCharsets.UTF_8);
    }
    @Test void sendsExactRecipientBrandedContentAndCapturesProviderId()throws Exception{
        HttpClient client=mock(HttpClient.class);reply(client,response(200,"{\"Messages\":[{\"Status\":\"success\",\"To\":[{\"Email\":\"actual+tag@example.test\",\"MessageID\":123456789}]}]}"));
        var receipt=transport(client,new StandardEnvironment()).sendTracked(message);
        assertThat(receipt).isEqualTo(new DeliveryReceipt("mailjet","123456789","ACCEPTED"));
        var captor=org.mockito.ArgumentCaptor.forClass(HttpRequest.class);verify(client).send(captor.capture(),any(HttpResponse.BodyHandler.class));var request=captor.getValue();
        assertThat(request.uri().toString()).isEqualTo("https://api.mailjet.com/v3.1/send");
        assertThat(request.headers().firstValue("Authorization")).contains("Basic "+Base64.getEncoder().encodeToString("key-placeholder:secret-placeholder".getBytes(StandardCharsets.UTF_8)));
        var payload=json.readTree(body(request)).path("Messages").get(0);
        assertThat(payload.path("To").get(0).path("Email").asText()).isEqualTo(message.to());
        assertThat(payload.path("HTMLPart").asText()).contains("&lt;Name &amp; friend&gt;","cid:hootly-owl");
        assertThat(payload.path("TextPart").asText()).contains("483627");
        assertThat(payload.path("InlinedAttachments").get(0).path("ContentID").asText()).isEqualTo("hootly-owl");
        assertThat(payload.path("CustomID").asText()).isEqualTo(message.id().toString());
    }
    @Test void rejectsInvalidResponsesAndHandlesRateLimitsWithoutRetrying()throws Exception{
        for(int status:new int[]{401,429,500}){
            HttpClient client=mock(HttpClient.class);reply(client,response(status,"sensitive provider body"));
            assertThatThrownBy(()->transport(client,new StandardEnvironment()).sendTracked(message)).isInstanceOfSatisfying(MailDeliveryException.class,e->{assertThat(e.uncertain()).isEqualTo(status==500);assertThat(e.getMessage()).doesNotContain("sensitive");if(status==401)assertThat(e.category()).isEqualTo("authentication_rejected");if(status==429)assertThat(e.category()).isEqualTo("rate_limited");});
            verify(client,times(1)).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        }
        HttpClient rejected=mock(HttpClient.class);reply(rejected,response(200,"{\"Messages\":[{\"Status\":\"error\"}]}"));
        assertThatThrownBy(()->transport(rejected,new StandardEnvironment()).sendTracked(message)).isInstanceOfSatisfying(MailDeliveryException.class,e->assertThat(e.uncertain()).isFalse());
        HttpClient senderRejected=mock(HttpClient.class);reply(senderRejected,response(400,"{\"Messages\":[{\"Status\":\"error\",\"Errors\":[{\"ErrorMessage\":\"Sender address is not verified\"}]}]}"));
        assertThatThrownBy(()->transport(senderRejected,new StandardEnvironment()).sendTracked(message)).isInstanceOfSatisfying(MailDeliveryException.class,e->{assertThat(e.category()).isEqualTo("sender_not_verified");assertThat(e.getMessage()).doesNotContain("Sender address");});
        HttpClient malformed=mock(HttpClient.class);reply(malformed,response(200,"{}"));
        assertThatThrownBy(()->transport(malformed,new StandardEnvironment()).sendTracked(message)).isInstanceOfSatisfying(MailDeliveryException.class,e->assertThat(e.uncertain()).isTrue());
        HttpClient offline=mock(HttpClient.class);when(offline.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenThrow(new IOException("secret should not escape"));
        assertThatThrownBy(()->transport(offline,new StandardEnvironment()).sendTracked(message)).isInstanceOfSatisfying(MailDeliveryException.class,e->{assertThat(e.uncertain()).isTrue();assertThat(e.getMessage()).doesNotContain("secret");});
    }
    @Test void testProfilesCannotSendEvenWhenKeysArePresent()throws Exception{
        for(String profile:List.of("test","smtp-capture")){HttpClient client=mock(HttpClient.class);var env=new StandardEnvironment();env.setActiveProfiles(profile);var transport=transport(client,env);assertThat(transport.configured()).isFalse();assertThatThrownBy(()->transport.send(message)).isInstanceOf(MailDeliveryException.class);verifyNoInteractions(client);}
    }
    @Test void providerSettingSelectsExactlyOneTransport(){
        var runner=new ApplicationContextRunner().withUserConfiguration(Transports.class);
        runner.withPropertyValues("app.mail.provider=mailjet","app.mail.mailjet.api-key=key-placeholder","app.mail.mailjet.secret-key=secret-placeholder","app.mail.mailjet.from-email=verified@example.test","app.mail.mailjet.from-name=Hootly").run(context->{assertThat(context).hasSingleBean(EmailTransport.class);assertThat(context.getBean(EmailTransport.class)).isInstanceOf(MailjetEmailTransport.class);assertThat(context.getBean(EmailTransport.class).configured()).isTrue();});
        runner.withPropertyValues("app.mail.provider=mailjet","app.mail.mailjet.api-key=key-placeholder","app.mail.mailjet.secret-key=secret-placeholder").run(context->{assertThat(context).hasSingleBean(EmailTransport.class);assertThat(context.getBean(EmailTransport.class)).isInstanceOf(MailjetEmailTransport.class);assertThat(context.getBean(EmailTransport.class).configured()).isFalse();});
        runner.withPropertyValues("app.mail.provider=smtp").run(context->{assertThat(context).doesNotHaveBean(EmailTransport.class);});
    }
}
