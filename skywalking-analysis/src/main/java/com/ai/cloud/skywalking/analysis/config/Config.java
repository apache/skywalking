package com.ai.cloud.skywalking.analysis.config;

public class Config {
    public static class Reducer{
        public static int REDUCER_NUMBER = 1;
    }

    public static class HBase {
        public static String TRACE_DETAIL_FAMILY_COLUMN = "chain_detail";

        public static String CHAIN_SUMMARY_COLUMN_FAMILY = "chain_summary";

        public static String TRACE_INFO_COLUMN_FAMILY = "trace_info";

        public static String ZK_QUORUM;

        public static String ZK_CLIENT_PORT;

        public static String TRACE_INFO_TABLE_NAME = "trace-info";

        public static String TABLE_CALL_CHAIN_RELATIONSHIP = "sw-chain-relationship";

        public static String CHAIN_RELATIONSHIP_COLUMN_FAMILY = "chain-relationship";

        public static String TABLE_CHAIN_INFO = "sw-chain-info";

        public static String TABLE_CHAIN_SUMMARY = "sw-chain-summary";

        public static String TABLE_CHAIN_DETAIL = "sw-chain-detail";

        public static String TABLE_CALL_CHAIN = "sw-call-chain";
    }

    public static class TraceInfo {
        public static String TRACE_INFO_COLUMN_CID = "cid";
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
        public static long INTERVAL = 5L;
    }
}
