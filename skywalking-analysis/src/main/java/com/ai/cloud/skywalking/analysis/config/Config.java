package com.ai.cloud.skywalking.analysis.config;

public class Config {
    public static class HBase {


        public static String TRACE_DETAIL_FAMILY_COLUMN = "chain_detail";

        public static String CHAIN_SUMMARY_COLUMN_FAMILY = "chain_summary";

        public static String TRACE_INFO_COLUMN_FAMILY = "trace_info";

        public static String ZK_QUORUM = "10.1.235.197,10.1.235.198,10.1.235.199";

        public static String ZK_CLIENT_PORT = "29181";

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

        public static String URL = "jdbc:mysql://10.1.228.202:31316/test";

        public static String USERNAME = "devrdbusr21";

        public static String PASSWORD = "devrdbusr21";

        public static String DRIVER_CLASS = "com.mysql.jdbc.Driver";
    }

    public static class Filter {
        public static String FILTER_PACKAGE_NAME = "com.ai.cloud.skywalking.analysis.categorize2chain.filter.impl";
    }

    public class ChainNodeSummary {
        public static final long INTERVAL = 5L;
    }
}
