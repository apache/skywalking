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

    public static class AnalysisServer {
        public static boolean IS_ACCUMULATE_MODE = true;
    }

    public static class MapReduce {
        public static String JAVA_OPTS = "-Xmx200m";
    }


    public static class Redis {
        public static String HOST = "127.0.0.1";

        public static int PORT = 6379;

        public static String MAPPER_COUNT_KEY = "ANALYSIS_TOTAL_SIZE";

        public static String SUCCESS_MAPPER_COUNT_KEY = "ANALYSIS_SUCCESS_TOTAL_SIZE";

        public static  String FAILED_MAPPER_COUNT_KEY = "ANALYSIS_FAILED_TOTAL_SIZE";
    }
}
