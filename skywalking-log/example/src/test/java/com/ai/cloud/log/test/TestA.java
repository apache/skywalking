package com.ai.cloud.log.test;

import org.apache.log4j.Logger;

public class TestA {

    private Logger logger = Logger.getLogger(TestA.class);

    public void logHelloWorld() {
        logger.info("Hello World");
        new TestB().log2jHelloWorld();
    }

    public static void main(String[] args) {
        new TestA().logHelloWorld();
    }
}
