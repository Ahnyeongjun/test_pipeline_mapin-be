package com.mapin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class MapinPipelineApplication {

    public static void main(String[] args) {
        SpringApplication.run(MapinPipelineApplication.class, args);
    }
}
