package com.a.eye.skywalking.web.config;

/**
 * Created by xin on 2016/11/2.
 */
public class Config {
    public static class RegistryCenter {

        public static String AUTH_INFO = "";

        public static String AUTH_SCHEMA = "";

        public static String CONNECT_URL = "127.0.0.1:2181";

        public static String PATH_PREFIX = "/skywalking/storage_list/";
    }

    public static class RoutingNode {
        public static String SUBSCRIBE_PATH = "/skywalking/routing_list";
    }
}
