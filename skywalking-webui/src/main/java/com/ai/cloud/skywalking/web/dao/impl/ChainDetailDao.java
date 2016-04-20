package com.ai.cloud.skywalking.web.dao.impl;

import com.ai.cloud.skywalking.web.dao.inter.IChainDetailDao;
import com.ai.cloud.skywalking.web.dto.HitTreeInfo;
import com.ai.cloud.skywalking.web.util.Constants;
import com.ai.cloud.skywalking.web.util.DBConnectUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class ChainDetailDao implements IChainDetailDao {

    private Logger logger = LogManager.getLogger(SystemConfigMaintainDao.class);

    @Autowired
    private DBConnectUtil dbConnectUtil;

    @Override
    public List<HitTreeInfo> queryChainTreeIds(String uid, String viewpoint, int pageSize) throws SQLException {
        List<HitTreeInfo> hitTreeInfos = new ArrayList<HitTreeInfo>();
        String sql = "SELECT DISTINCT treeId FROM sw_chain_detail WHERE uid = ? AND viewpoint like ? LIMIT ?, ?";
        Connection connection = dbConnectUtil.getConnection();
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setString(1, uid);
            preparedStatement.setString(2, "%" + viewpoint + "%");
            preparedStatement.setInt(3, (pageSize - 1) * Constants.MAX_ANALYSIS_RESULT_PAGE_SIZE);
            preparedStatement.setInt(4, Constants.MAX_ANALYSIS_RESULT_PAGE_SIZE + 1);
            logger.info("startsize : {} ", (pageSize - 1) * Constants.MAX_ANALYSIS_RESULT_PAGE_SIZE);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                HitTreeInfo hitTreeInfo = new HitTreeInfo(resultSet.getString("treeId"), uid);
                Map<String, String> result = queryChainTreeId(uid, hitTreeInfo.getTreeId(), viewpoint);
                for (Map.Entry<String, String> entry : result.entrySet()) {
                    hitTreeInfo.addHitTraceLevelId(entry.getKey(), entry.getValue());
                }
                hitTreeInfos.add(hitTreeInfo);
            }
        } catch (Exception e) {
            logger.error("Failed to query treeIds for Viewpoint[{}]", viewpoint);
            throw new RuntimeException("Failed to query treeIds for " + viewpoint, e);
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
        logger.info("hitTreeInfos : {}", hitTreeInfos.size());
        return hitTreeInfos;
    }

    private Map<String, String> queryChainTreeId(String uid, String treeId, String viewpoint) throws SQLException {
        Map<String, String> result = new HashMap<String, String>();
        String sql = "SELECT DISTINCT traceLevelId, viewpoint FROM sw_chain_detail WHERE uid = ? AND treeId=? AND viewpoint like ?";
        Connection connection = dbConnectUtil.getConnection();
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setString(1, uid);
            preparedStatement.setString(2, treeId);
            preparedStatement.setString(3, "%" + viewpoint + "%");
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                result.put(resultSet.getString("traceLevelId"), resultSet.getString("viewpoint"));
            }
        } catch (Exception e) {
            logger.error("Failed to query treeIds for Viewpoint[{}]", viewpoint);
            throw new RuntimeException("Failed to query treeIds for " + viewpoint, e);
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
        return result;
    }


    @Override
    public String queryChainViewPoint(String traceLevelId, String treeId, String uid) throws SQLException {
        String viewpoint = null;
        String sql = "SELECT DISTINCT viewpoint FROM sw_chain_detail WHERE uid = ? AND treeId = ? AND traceLevelId = ? ";
        Connection connection = dbConnectUtil.getConnection();
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setString(1, uid);
            preparedStatement.setString(2, treeId);
            preparedStatement.setString(3, traceLevelId);

            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                viewpoint = resultSet.getString("viewpoint");
            }
        } catch (Exception e) {
            logger.error("Failed to query treeIds for Viewpoint[{}]", viewpoint);
            throw new RuntimeException("Failed to query treeIds for " + viewpoint, e);
        } finally {
            if (connection != null) {
                connection.close();
            }
        }

        return viewpoint;
    }
}
