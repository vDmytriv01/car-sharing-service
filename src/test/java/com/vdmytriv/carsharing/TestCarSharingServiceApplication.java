package com.vdmytriv.carsharing;

import org.springframework.boot.SpringApplication;

public class TestCarSharingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(CarSharingServiceApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
