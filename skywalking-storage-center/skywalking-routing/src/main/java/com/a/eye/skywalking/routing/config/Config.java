package com.a.eye.skywalking.routing.config;

public class Config {
    public static class Routing {
        public static int PORT = 23000;
    }

    public static class RegistryCenter {
        public static String TYPE = "zookeeper";
        public static String CONNECT_URL = "127.0.0.1:2181";
        public static String AUTH_SCHEMA = "";
        public static String AUTH_INFO = "";
    }

    public static class StorageNode {
        public static String SUBSCRIBE_PATH = "/skywalking/storage_list";
    }

    public static class Disruptor {
        public static int BUFFER_SIZE = 2 ^ 10;
    }
}
