package com.ai.cloud.skywalking.web.dao.inter;

import com.ai.cloud.skywalking.web.dto.TypicalCallTree;

import java.io.IOException;

/**
 * Created by xin on 16-4-28.
 */
public interface ITypicalCallTreeDao {
    String[] queryAllCombineCallChainTreeIds(String rowKey) throws IOException;

    TypicalCallTree queryCallChainTree(String callTreeId) throws IOException;
}
