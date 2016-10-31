package com.a.eye.skywalking.storage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Main {

    private static final Logger logger = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        logger.info("Init config param.");
        //
        new DataStorager().start();
        logger.info("start success.");
    }
}
