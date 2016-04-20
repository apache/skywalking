package com.ai.cloud.skywalking.web.dao.inter;

import com.ai.cloud.skywalking.web.dto.AnlyResult;

import java.io.IOException;

/**
 * Created by xin on 16-4-6.
 */
public interface ICallChainTreeDao {
    AnlyResult queryEntranceAnlyResult(String entranceColumnName, String treeId) throws IOException;
}
