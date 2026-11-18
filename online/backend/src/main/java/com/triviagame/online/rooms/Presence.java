package com.triviagame.online.rooms;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class Presence {
    private final ConcurrentHashMap<String,ConcurrentHashMap<UUID,Integer>> rooms=new ConcurrentHashMap<>();
    public void connected(String code,UUID user){rooms.computeIfAbsent(code,key->new ConcurrentHashMap<>()).merge(user,1,Integer::sum);}
    public void disconnected(String code,UUID user){var members=rooms.get(code);if(members!=null){members.computeIfPresent(user,(key,count)->count<=1?null:count-1);if(members.isEmpty())rooms.remove(code,members);}}
    public boolean isConnected(String code,UUID user){var members=rooms.get(code);return members!=null&&members.containsKey(user);}
}
