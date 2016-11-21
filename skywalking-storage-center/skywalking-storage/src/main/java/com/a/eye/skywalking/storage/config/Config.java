package com.a.eye.skywalking.storage.config;

/**
 * Created by xin on 2016/11/2.
 */
public class Config {
    public static class Server {
        public static int PORT = 34000;
    }


    public static class DataConsumer {

        public static int CHANNEL_SIZE = 10;

        public static int BUFFER_SIZE = 1000;

        public static int CONSUMER_SIZE = 5;
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

}
