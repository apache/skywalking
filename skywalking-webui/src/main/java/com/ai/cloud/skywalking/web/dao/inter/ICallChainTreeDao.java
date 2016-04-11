package com.ai.cloud.skywalking.web.dao.inter;

import com.ai.cloud.skywalking.web.entity.CallChainTree;

import java.io.IOException;

/**
 * Created by xin on 16-4-6.
 */
public interface ICallChainTreeDao {
    CallChainTree queryTreeId(String treeId) throws IOException;
}
