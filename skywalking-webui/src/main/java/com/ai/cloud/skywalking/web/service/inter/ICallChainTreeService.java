package com.ai.cloud.skywalking.web.service.inter;

import com.ai.cloud.skywalking.web.entity.CallChainTree;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * Created by xin on 16-4-6.
 */
public interface ICallChainTreeService {
    List<CallChainTree> queryCurrentMonthCallChainTree(String uid, String viewpoint) throws SQLException, IOException;
}
