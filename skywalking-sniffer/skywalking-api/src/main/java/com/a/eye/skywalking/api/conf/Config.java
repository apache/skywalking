package com.a.eye.skywalking.api.conf;

import com.a.eye.skywalking.api.logging.LogLevel;

public class Config {

    public static class Agent {
        public static String APPLICATION_CODE = "";

        public static int SAMPLING_CYCLE = 1;
    }

    public static class Collector {
        public static String SERVERS = "";

        public static String SERVICE_NAME = "/segments";

        public static int BATCH_SIZE = 50;
    }

    public static class Buffer {
        public static int SIZE = 512;
    }

    public static class Logging {
        // log文件名
        public static String FILE_NAME = "skywalking-api.log";
        // log文件文件夹名字
        public static String DIR = "";
        // 最大文件大小
        public static int MAX_FILE_SIZE = 300 * 1024 * 1024;

        public static LogLevel LEVEL = LogLevel.DEBUG;
    }
}
