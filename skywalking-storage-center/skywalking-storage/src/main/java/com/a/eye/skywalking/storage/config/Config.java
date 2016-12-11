package com.a.eye.skywalking.storage.config;

/**
 * Created by xin on 2016/11/2.
 */
public class Config {
    public static class Server {
        public static int PORT = 34000;
    }

    public static class Disruptor {
        public static int BUFFER_SIZE = 1024 * 128;

        public static int FLUSH_SIZE = 100;
    }


    public static class DataFile {
        public static String PATH = "/data/file";

        public static long SIZE = 3 * 1024 * 1024 * 1024L;
    }

    public static class IndexOperator {

        public static class Finder {

            public static int TOTAL = 50;

            public static int IDEL = 20;
        }
    }


    public static class RegistryCenter {

        public static String AUTH_INFO = "";

        public static String AUTH_SCHEMA = "";

        public static String CONNECT_URL = "127.0.0.1:2181";

        public static String PATH_PREFIX = "/skywalking/storage_list/";
    }

    public static class Alarm {

        public static String REDIS_SERVER = "127.0.0.1:6379";

        public static boolean ALARM_OFF_FLAG = false;

        public static int ALARM_EXCEPTION_STACK_LENGTH = 300;

        public static long ALARM_REDIS_INSPECTOR_INTERVAL = 100;

        public static int REDIS_MAX_IDLE = 10;

        public static int REDIS_MIN_IDLE = 1;

        public static int REDIS_MAX_TOTAL = 30;

        public static int ALARM_EXPIRE_SECONDS = 1000 * 60 * 90;
    }
}
