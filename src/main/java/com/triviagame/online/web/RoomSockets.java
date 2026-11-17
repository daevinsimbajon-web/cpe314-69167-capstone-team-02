package com.triviagame.online.web;

import com.triviagame.online.rooms.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.session.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.handler.*;
import org.springframework.web.socket.server.HandshakeInterceptor;

/** WebSocket messages only invalidate views. Each client fetches its own authorized projection. */
@Component
public class RoomSockets extends TextWebSocketHandler implements HandshakeInterceptor {
    private final RoomService rooms;
    private final Presence presence;
    private final SessionRepository<? extends Session> sessions;
    private final Map<String,WebSocketSession> connected=new ConcurrentHashMap<>();
    public RoomSockets(RoomService rooms,Presence presence,SessionRepository<? extends Session> sessions){this.rooms=rooms;this.presence=presence;this.sessions=sessions;}
    @Override public boolean beforeHandshake(ServerHttpRequest request,ServerHttpResponse response,WebSocketHandler handler,Map<String,Object> attributes){
        response.setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN);
        if(!(request instanceof ServletServerHttpRequest servlet)||request.getPrincipal()==null)return false;
        var session=servlet.getServletRequest().getSession(false);if(session==null)return false;
        String path=request.getURI().getPath();String code=path.substring(path.lastIndexOf('/')+1).toUpperCase(Locale.ROOT);
        UUID user;
        try{user=UUID.fromString(request.getPrincipal().getName());if(!rooms.mayConnect(code,user))return false;}
        catch(RuntimeException ex){return false;}
        attributes.put("code",code);attributes.put("user",user);attributes.put("session",session.getId());response.setStatusCode(org.springframework.http.HttpStatus.OK);return true;
    }
    @Override public void afterHandshake(ServerHttpRequest request,ServerHttpResponse response,WebSocketHandler handler,Exception failure){ }
    @Override public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        UUID user=(UUID)session.getAttributes().get("user");
        if(connected.values().stream().filter(s->s.getAttributes().get("user").equals(user)).count()>=4){session.close(CloseStatus.POLICY_VIOLATION);return;}
        connected.put(session.getId(),new ConcurrentWebSocketSessionDecorator(session,3000,64000));
        String code=(String)session.getAttributes().get("code");presence.connected(code,user);broadcast(code);
    }
    @Override public void afterConnectionClosed(WebSocketSession session,CloseStatus status){
        if(connected.remove(session.getId())!=null){String code=(String)session.getAttributes().get("code");presence.disconnected(code,(UUID)session.getAttributes().get("user"));broadcast(code);}
    }
    @Override protected void handleTextMessage(WebSocketSession session,TextMessage message) throws Exception {
        session.close(CloseStatus.POLICY_VIOLATION.withReason("Use authenticated HTTP commands"));
    }
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT,fallbackExecution=true)
    public void roomChanged(RoomChanged event){broadcast(event.code());}
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void profileChanged(com.triviagame.online.auth.AccountProfileChanged event){rooms.list(event.user()).forEach(room->broadcast(room.code()));}
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void credentialsChanged(com.triviagame.online.auth.AccountCredentialsChanged event){connected.values().stream().filter(session->event.user().equals(session.getAttributes().get("user"))).forEach(session->{try{session.close(CloseStatus.POLICY_VIOLATION.withReason("Please sign in again"));}catch(IOException ignored){/* Already closed. */}});}
    @Scheduled(fixedDelay=15000) public void heartbeat(){
        connected.values().stream().map(s->(String)s.getAttributes().get("code")).distinct().forEach(this::broadcast);
    }
    private void broadcast(String code){
        for(WebSocketSession session:connected.values()){
            if(!code.equals(session.getAttributes().get("code")))continue;
            try{
                if(sessions.findById((String)session.getAttributes().get("session"))==null||!rooms.mayConnect(code,(UUID)session.getAttributes().get("user"))){
                    session.close(CloseStatus.POLICY_VIOLATION.withReason("Session or room access expired"));continue;
                }
                session.sendMessage(new TextMessage("{\"type\":\"ROOM_UPDATED\"}"));
            }catch(IOException|RuntimeException ex){try{session.close(CloseStatus.SERVER_ERROR);}catch(IOException ignored){/* Connection is already gone. */}}
        }
    }
    @Configuration @EnableWebSocket
    static class SocketConfiguration implements WebSocketConfigurer {
        private final RoomSockets sockets;private final String[] origins;
        SocketConfiguration(RoomSockets sockets,@Value("${app.allowed-origins}") String origins){this.sockets=sockets;this.origins=Arrays.stream(origins.split(",")).map(String::strip).toArray(String[]::new);}
        @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry){registry.addHandler(sockets,"/ws/rooms/*").addInterceptors(sockets).setAllowedOrigins(origins);}
    }
}
