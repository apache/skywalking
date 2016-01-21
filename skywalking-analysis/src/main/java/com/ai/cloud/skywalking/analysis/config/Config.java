package com.ai.cloud.skywalking.analysis.config;

public class Config {
    public static class HBase {

        public static String CHAIN_SUMMARY_COLUMN_FAMILY = "chain_summary";

        public static String TRACE_INFO_COLUMN_FAMILY = "trace_info";

        public static String CALL_CHAIN_TABLE_NAME;

        public static String ZK_QUORUM;

        public static String ZK_CLIENT_PORT;

        public static String TRACE_INFO_TABLE_NAME = "trace_info";

        public static String TABLE_CALL_CHAIN_RELATIONSHIP = "sw_chain_relationship";

        public static String CHAIN_RELATIONSHIP_COLUMN_FAMILY = "chain_relationship";

        public static String TABLE_CHAIN_INFO = "sw_chain_info";

        public static String TABLE_CHAIN_SUMMARY = "sw_chain_summary";

    }

    public static class TraceInfo {
        public static String TRACE_INFO_COLUMN_CID = "cid";
    }

    public static class MySql {

        public static String url;

        public static String userName;

        public static String password;

        public static String driverClass;
    }

    public static class Filter {
        public static String FILTER_PACKAGE_NAME;
    }

    public class ChainNodeSummary {
        public static final long INTERVAL = 5L;
    }
}
