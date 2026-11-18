package com.triviagame.online;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OnlineApplication {
    public static void main(String[] args) { SpringApplication.run(OnlineApplication.class, args); }
    @Bean Clock gameClock() { return Clock.tickMillis(java.time.ZoneOffset.UTC); }
}
