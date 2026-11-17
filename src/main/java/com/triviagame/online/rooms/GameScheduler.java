package com.triviagame.online.rooms;

import java.time.*;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @ConditionalOnProperty(name="app.scheduler-enabled",havingValue="true",matchIfMissing=true)
public class GameScheduler {
    private static final Logger LOG=LoggerFactory.getLogger(GameScheduler.class);
    private final RoomService rooms;private final JdbcTemplate jdbc;private final Clock clock;
    public GameScheduler(RoomService rooms,JdbcTemplate jdbc,Clock clock){this.rooms=rooms;this.jdbc=jdbc;this.clock=clock;}
    @Scheduled(fixedDelay=500) public void advanceDeadlines(){
        for(String code:jdbc.queryForList("SELECT code FROM quiz_rooms WHERE (status IN ('QUESTION','REVEAL') AND deadline<=?) OR (status='LOBBY' AND expires_at<=?)",
                String.class,OffsetDateTime.ofInstant(clock.instant(),ZoneOffset.UTC),OffsetDateTime.ofInstant(clock.instant(),ZoneOffset.UTC))){
            try{rooms.tick(code);}catch(RuntimeException ex){LOG.error("Could not advance room {}",code,ex);}
        }
    }
}
