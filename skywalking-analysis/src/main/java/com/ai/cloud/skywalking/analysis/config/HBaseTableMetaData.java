package com.ai.cloud.skywalking.analysis.config;

public class HBaseTableMetaData {
    /**
     * 调用链明细表，前端收集程序入库数据
     *
     * @author wusheng
     */
    public final static class TABLE_CALL_CHAIN {
        public static final String TABLE_NAME = "sw-call-chain";
    }

    /**
     * HBase 表：用于存放CID，TID的映射关系表
     *
     * @author wusheng
     */
    public final static class TABLE_CID_TID_MAPPING {
        public static final String TABLE_NAME = "sw-cid-tid-mapping";

        public static final String COLUMN_FAMILY_NAME = "trace_info";

        public static final String CID_COLUMN_NAME = "cid";
    }

    /**
     * CID明细信息表
     *
     * @author wusheng
     */
    public final static class TABLE_CHAIN_DETAIL {
        public static final String TABLE_NAME = "sw-chain-detail";

        public static final String COLUMN_FAMILY_NAME = "chain_detail";
    }

    /**
     * CID间关系表，记录异常CID和正常CID间的归属关系
     *
     * @author wusheng
     */
    public final static class TABLE_CALL_CHAIN_RELATIONSHIP {
        public static final String TABLE_NAME = "sw-chain-relationship";

        public static final String COLUMN_FAMILY_NAME = "chain-relationship";

        public static final String UNCATEGORIZE_COLUMN_NAME = "UNCATEGORIZED_CALL_CHAIN";
    }

    /**
     * 用于存放每个CID在一分钟内的汇总，汇总结果不包含关系汇总
     *
     * @author wusheng
     */
    public final static class TABLE_CHAIN_ONE_MINUTE_SUMMARY_EXCLUDE_RELATIONSHIP {
        public static final String TABLE_NAME = "sw-chain-1min-summary-ex-rela";

        public static final String COLUMN_FAMILY_NAME = "chain_summary";
    }

    /**
     * 用于存放每个CID在一分钟内的汇总，汇总结果不包含关系汇总
     *
     * @author wusheng
     */
    public final static class TABLE_CHAIN_ONE_MINUTE_SUMMARY_INCLUDE_RELATIONSHIP {
        public static final String TABLE_NAME = "sw-chain-1min-summary-ic-rela";

        public static final String COLUMN_FAMILY_NAME = "chain_summary";
    }

    /**
     * 用于存放每个CID在一小时内的汇总，汇总结果不包含关系汇总
     *
     * @author wusheng
     */
    public final static class TABLE_CHAIN_ONE_HOUR_SUMMARY_INCLUDE_RELATIONSHIP {
        public static final String TABLE_NAME = "sw-chain-1hour-summary-ic-rela";

        public static final String COLUMN_FAMILY_NAME = "chain_summary";
    }

    /**
     * 用于存放每个CID在一天内的汇总，汇总结果不包含关系汇总
     *
     * @author wusheng
     */
    public final static class TABLE_CHAIN_ONE_DAY_SUMMARY_INCLUDE_RELATIONSHIP {
        public static final String TABLE_NAME = "sw-chain-1day-summary-ic-rela";

        public static final String COLUMN_FAMILY_NAME = "chain_summary";
    }

    /**
     * 用于存放每个CID在一月内的汇总，汇总结果不包含关系汇总
     *
     * @author wusheng
     */
    public final static class TABLE_CHAIN_ONE_MONTH_SUMMARY_INCLUDE_RELATIONSHIP {
        public static final String TABLE_NAME = "sw-chain-1mon-summary-ic-rela";

        public static final String COLUMN_FAMILY_NAME = "chain_summary";
    }
}
