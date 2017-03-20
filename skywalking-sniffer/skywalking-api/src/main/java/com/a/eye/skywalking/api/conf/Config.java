package com.a.eye.skywalking.api.conf;

public class Config {

    public static class SkyWalking {
        public static String APPLICATION_CODE = "";

        public static boolean IS_PREMAIN_MODE = false;

        public static String AGENT_BASE_PATH = "";

        public static String SERVERS = "";

        public static String SERVICE_NAME = "/segments";
    }

    public static class Disruptor{
        public static int BUFFER_SIZE = 512;
    }


    public static class Logging {
        // log文件名
        public static String LOG_FILE_NAME              = "skywalking-api.log";
        // log文件文件夹名字
        public static String LOG_DIR_NAME               = "logs";
        // 最大文件大小
        public static int    MAX_LOG_FILE_LENGTH        = 300 * 1024 * 1024;
        // skywalking 系统错误文件日志
        public static String SYSTEM_ERROR_LOG_FILE_NAME = "skywalking-api-error.log";
    }
}
