package com.ai.cloud.skywalking.web.dao.inter;

import com.ai.cloud.skywalking.web.dto.HitTreeInfo;

import java.sql.SQLException;
import java.util.List;

/**
 * Created by xin on 16-4-6.
 */
public interface IChainDetailDao {
    List<HitTreeInfo> queryChainTreeIds(String uid, String viewpoint) throws SQLException;

    String queryChainViewPoint(String traceLevelId, String treeId, String uid) throws SQLException;
}
