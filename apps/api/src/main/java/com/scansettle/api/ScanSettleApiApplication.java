package com.scansettle.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ScanSettleApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScanSettleApiApplication.class, args);
    }
}
