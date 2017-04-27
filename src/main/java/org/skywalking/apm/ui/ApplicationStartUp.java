package org.skywalking.apm.ui;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@SpringBootApplication
@Configuration
@EnableAutoConfiguration
@ComponentScan("org.skywalking.apm.ui")
public class ApplicationStartUp extends SpringBootServletInitializer {

    public static void main(String[] args) throws Exception {
        SpringApplication.run(ApplicationStartUp.class, args);
    }
}
