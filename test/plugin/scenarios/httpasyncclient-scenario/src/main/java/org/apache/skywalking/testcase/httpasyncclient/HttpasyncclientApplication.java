package org.apache.skywalking.testcase.httpasyncclient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HttpasyncclientApplication {

    public static void main(String[] args) {

        Object[] sources = new Object[] {HttpasyncclientApplication.class};
        SpringApplication.run(sources, args);

    }
}
