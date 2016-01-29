package com.ai.cloud.skywalking.analysis.config;

public class Constants {
    public static final String UNCATEGORIZE_COLUMN_FAMILY = "UNCATEGORIZED_CALL_CHAIN";

    // HBase 表：用于存放CID，TID的映射关系表
    public static String TABLE_CID_TID_MAPPING = "sw-cid-tid-mapping";

    //HBase 表：用于存放调用链一分钟的汇总，汇总结果不包含关系汇总
    public static String TABLE_CHAIN_ONE_MINUTE_SUMMARY_EXCLUDE_RELATIONSHIP = "sw-chain-1min-summary-ex-rela";

    public static String TABLE_CHAIN_DETAIL = "sw-chain-detail";

    public static String TABLE_CALL_CHAIN = "sw-call-chain";

    public static String TABLE_CALL_CHAIN_RELATIONSHIP = "sw-chain-relationship";

    public static String COLUMN_FAMILY_CHAIN_RELATIONSHIP = "chain-relationship";

    public static String COLUMN_FAMILY_NAME_TRACE_DETAIL = "chain_detail";

    public static String COLUMN_FAMILY_NAME_CHAIN_SUMMARY = "chain_summary";

    public static String COLUMN_FAMILY_NAME_TRACE_INFO = "trace_info";

    public static String COLUMN_FAMILY_NAME_CID = "cid";
}
