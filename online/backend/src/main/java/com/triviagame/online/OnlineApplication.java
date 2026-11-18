package com.triviagame.online;

import com.triviagame.api.OpenTdbClient;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OnlineApplication {
    public static void main(String[] args) { SpringApplication.run(OnlineApplication.class, args); }
    @Bean Clock gameClock() { return Clock.tickMillis(java.time.ZoneOffset.UTC); }

    @Bean
    OpenTdbClient openTdbClient(
            @Value("${app.opentdb.endpoint:https://opentdb.com/api.php}") String endpoint,
            @Value("${app.opentdb.timeout-ms:12000}") long timeout) {
        return new OpenTdbClient(URI.create(endpoint), Duration.ofMillis(timeout));
    }
}
