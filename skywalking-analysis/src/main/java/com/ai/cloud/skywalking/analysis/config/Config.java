package com.ai.cloud.skywalking.analysis.config;

public class Config {
    public static class HBase {

        public static String TRACE_INFO_COLUMN_FAMILY = "trace_info";

        public static String CALL_CHAIN_TABLE_NAME;

        public static String ZK_QUORUM;

        public static String ZK_CLIENT_PORT;

        public static String TRACE_INFO_TABLE_NAME;


    }

    public static class TraceInfo {

        public static String PARENT_LEVEL_ID = "parentLevelId";

        public static String LEVEL_ID = "levelId";

        public static String BUSINESS_KEY = "businessKey";

        public static String COST = "cost";

        public static String TRACE_INFO_COLUMN_CID = "cid";

        public static String STATUS = "status";

        public static String USER_ID = "UId";
    }

    public static class Filter {
        public static String FILTER_PACKAGE_NAME;
    }
}
