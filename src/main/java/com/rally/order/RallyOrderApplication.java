package com.rally.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
        "com.rally.order",
        "com.rally.common.*"
})
public class RallyOrderApplication {
    public static void main(String[] args) {
        SpringApplication.run(RallyOrderApplication.class, args);
    }
}
