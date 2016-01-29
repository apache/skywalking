package com.ai.cloud.skywalking.analysis.config;

public class Config {
    public static class Reducer {
        public static int REDUCER_NUMBER = 1;
    }

    public static class HBase {
        public static String ZK_QUORUM;

        public static String ZK_CLIENT_PORT;
    }

    public static class MySql {

        public static String URL;

        public static String USERNAME;

        public static String PASSWORD;

        public static String DRIVER_CLASS = "com.mysql.jdbc.Driver";
    }

    public static class Filter {
        public static String FILTER_PACKAGE_NAME;
    }

    public static class ChainNodeSummary {
        public static long INTERVAL = 1L;
    }
}
