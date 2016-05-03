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
     * CID明细信息表
     *
     * @author wusheng
     */
    public final static class TABLE_CHAIN_DETAIL {
        public static final String TABLE_NAME = "sw-chain-detail";

        public static final String COLUMN_FAMILY_NAME = "chain_detail";
    }

    /**
     * 用于存放每个CID在一分钟内的汇总，汇总结果不包含关系汇总
     *
     * @author wusheng
     */
    public final static class TABLE_CHAIN_ONE_MINUTE_SUMMARY {
        public static final String TABLE_NAME = "sw-chain-1min-summary";

        public static final String COLUMN_FAMILY_NAME = "chain_summary";
    }

    /**
     * 用于存放每个CID在一小时内的汇总，汇总结果不包含关系汇总
     *
     * @author zhangxin
     */
    public final static class TABLE_CHAIN_ONE_HOUR_SUMMARY {
        public static final String TABLE_NAME = "sw-chain-1hour-summary";

        public static final String COLUMN_FAMILY_NAME = "chain_summary";
    }

    /**
     * 用于存放每个CID在一小时内的汇总，汇总结果不包含关系汇总
     *
     * @author zhangxin
     */
    public final static class TABLE_CHAIN_ONE_DAY_SUMMARY {
        public static final String TABLE_NAME = "sw-chain-1day-summary";

        public static final String COLUMN_FAMILY_NAME = "chain_summary";
    }

    /**
     * 用于存放每个CID在一小时内的汇总，汇总结果不包含关系汇总
     *
     * @author zhangxin
     */
    public final static class TABLE_CHAIN_ONE_MONTH_SUMMARY {
        public static final String TABLE_NAME = "sw-chain-1month-summary";

        public static final String COLUMN_FAMILY_NAME = "chain_summary";
    }

    /**
     * 用于存放调用树和CID的映射关系
     *
     * @author zhangxin
     */
    public final static class TABLE_CALL_CHAIN_TREE_ID_AND_CID_MAPPING {
        public static final String TABLE_NAME = "sw-treeId-cid-mapping";

        public static final String COLUMN_FAMILY_NAME = "cids";
        
        public static final String COLUMN_NAME = "been_merged_cid";
    }

    /**
     * 用于存放TraceID和CID的映射关系
     *
     * @author zhangxin
     */
    public final static class TABLE_TRACE_ID_AND_CID_MAPPING {
        public static final String TABLE_NAME = "sw-traceId-cid-mapping";

        public static final String COLUMN_FAMILY_NAME = "cid";

        public static final String COLUMN_NAME = "cid";
    }
}
