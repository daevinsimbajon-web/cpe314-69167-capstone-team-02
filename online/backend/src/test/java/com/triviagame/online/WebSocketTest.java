package com.triviagame.online;

import com.triviagame.online.web.RoomSockets;
import java.net.URI;
import java.net.http.*;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;

class WebSocketTest extends RoomTestSupport {
    @Autowired RoomSockets sockets;
    CompletableFuture<WebSocket> connect(Browser browser,String code,String origin,WebSocket.Listener listener) {
        String cookie=browser.cookies.getCookieStore().getCookies().stream().map(c->c.getName()+"="+c.getValue()).collect(Collectors.joining("; "));
        var builder=browser.client.newWebSocketBuilder().header("Origin",origin);
        if(!cookie.isBlank())builder.header("Cookie",cookie);
        return builder.buildAsync(URI.create("ws://localhost:"+port+"/ws/rooms/"+code),listener);
    }
    static class Updates implements WebSocket.Listener {
        final BlockingQueue<String> messages=new LinkedBlockingQueue<>();
        final CountDownLatch closed=new CountDownLatch(1);
        @Override public void onOpen(WebSocket socket){socket.request(1);}
        @Override public CompletionStage<?> onText(WebSocket socket,CharSequence data,boolean last){messages.add(data.toString());socket.request(1);return CompletableFuture.completedFuture(null);}
        @Override public CompletionStage<?> onClose(WebSocket socket,int status,String reason){closed.countDown();return CompletableFuture.completedFuture(null);}
    }
    @Test void roomMembersReceiveUpdatesAndLogoutClosesSocket() throws Exception {
        Browser host=registered(username()),player=registered(username());String code=create(host);Updates listener=new Updates();
        WebSocket socket=connect(host,code,"http://localhost:5173",listener).get(5,TimeUnit.SECONDS);
        try {
            assertThat(listener.messages.poll(5,TimeUnit.SECONDS)).contains("ROOM_UPDATED");
            assertThat(player.post("/rooms/"+code+"/join",Map.of()).status()).isEqualTo(200);
            assertThat(listener.messages.poll(5,TimeUnit.SECONDS)).contains("ROOM_UPDATED");
            var participants=host.get("/rooms/"+code).body().get("participants");
            var hostParticipant=java.util.stream.StreamSupport.stream(participants.spliterator(),false)
                    .filter(p->p.get("role").asText().equals("HOST")).findFirst().orElseThrow();
            assertThat(hostParticipant.get("connected").asBoolean()).isTrue();
            assertThat(host.post("/auth/logout",null).status()).isEqualTo(204);sockets.heartbeat();
            assertThat(listener.closed.await(5,TimeUnit.SECONDS)).isTrue();
        }finally{socket.abort();}
    }
    @Test void rejectsOutsidersUnauthenticatedSocketsAndUnapprovedOrigins() throws Exception {
        Browser host=registered(username()),outsider=registered(username());String code=create(host);
        for(var request:java.util.List.of(connect(outsider,code,"http://localhost:5173",new Updates()),
                connect(new Browser(),code,"http://localhost:5173",new Updates()),
                connect(host,code,"https://unapproved.example",new Updates()))) {
            assertThatThrownBy(()->request.get(5,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(WebSocketHandshakeException.class);
        }
    }
}
