package com.a.eye.skywalking.routing.config;

public class Config {
    public static class Server {

        public static String HOST = "0.0.0.0";

        public static int PORT = 23000;

        public static String REST_SERVICE_HOST = "0.0.0.0";

        public static int REST_SERVICE_PORT = 23100;
    }


    public static class Search {
        public static long CHECK_CYCLE = 100L;
        public static long TIMEOUT     = 3 * 1000L;
    }


    public static class RegistryCenter {
        public static String TYPE        = "zookeeper";

        public static String CONNECT_URL = "127.0.0.1:2181";

        public static String AUTH_SCHEMA = "";

        public static String AUTH_INFO   = "";

        public static String PATH_PREFIX = "/skywalking/routing_list/";
    }


    public static class StorageNode {
        public static String SUBSCRIBE_PATH = "/skywalking/storage_list";
    }


    public static class Disruptor {
        public static int BUFFER_SIZE = 1024 * 128 * 4;

        public static int FLUSH_SIZE = 100;
    }


    public static class Alarm {

        public static String REDIS_SERVER = "127.0.0.1:6379";

        public static boolean ALARM_OFF_FLAG = true;

        public static int ALARM_EXCEPTION_STACK_LENGTH = 300;

        public static long ALARM_REDIS_INSPECTOR_INTERVAL = 100;

        public static int REDIS_MAX_IDLE = 10;

        public static int REDIS_MIN_IDLE = 1;

        public static int REDIS_MAX_TOTAL = 30;

        public static int ALARM_EXPIRE_SECONDS = 1000 * 60 * 90;
    }
}
