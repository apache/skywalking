package com.ai.cloud.skywalking.web.dao.inter;

import java.sql.SQLException;
import java.util.List;

/**
 * Created by xin on 16-4-6.
 */
public interface IChainDetailDao {
    List<String> queryChainTreeIds(String uid, String viewpoint) throws SQLException;
}
