package com.ai.cloud.log.test;


import org.apache.logging.log4j.LogManager;

public class TestB {

    private org.apache.logging.log4j.Logger logger = LogManager.getLogger(TestB.class);

    public void log2jHelloWorld() {
        logger.info("{}", "Hello World");
    }
}
