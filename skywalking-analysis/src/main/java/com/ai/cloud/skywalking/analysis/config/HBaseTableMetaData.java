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
    public final static class TABLE_CHAIN_ONE_MINUTE_SUMMARY_EXCLUDE_RELATIONSHIP {
        public static final String TABLE_NAME = "sw-chain-1min-summary-ex-rela";

        public static final String COLUMN_FAMILY_NAME = "chain_summary";
    }

    /**
     * 用于存放每个CID在一分钟内的汇总，汇总结果不包含关系汇总
     *
     * @author wusheng
     */
    public final static class TABLE_CHAIN_ONE_MINUTE_SUMMARY {
        public static final String TABLE_NAME = "sw-chain-1min-summary-ic-rela";

        public static final String COLUMN_FAMILY_NAME = "chain_summary";
    }

    /**
     * 用于存放调用树和CID的映射关系
     *
     * @author zhangxin
     */
    public final static class TABLE_CALL_CHAIN_TREE_ID_AND_CID_MAPPING {
        public static final String TABLE_NAME = "sw-topologyId-cid-mapping";

        public static final String COLUMN_FAMILY_NAME = "sw-topologyId-cid-mapping";
    }

    public final static class TABLE_CALL_CHAIN_TREE_DETAIL {
        public static final String TABLE_NAME = "sw-call-chain-tree-detail";

        public static final String COLUMN_FAMILY_NAME = "tree-detail";
    }
}
