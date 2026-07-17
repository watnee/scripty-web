package com.scripty;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ScriptyApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScriptyApplication.class, args);
    }
}
