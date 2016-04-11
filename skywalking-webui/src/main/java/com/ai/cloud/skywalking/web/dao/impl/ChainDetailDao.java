package com.ai.cloud.skywalking.web.dao.impl;

import com.ai.cloud.skywalking.web.dao.inter.IChainDetailDao;
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
import java.util.List;

@Repository
public class ChainDetailDao implements IChainDetailDao {

    private Logger logger = LogManager.getLogger(SystemConfigMaintainDao.class);

    @Autowired
    private DBConnectUtil dbConnectUtil;

    @Override
    public List<String> queryChainTreeIds(String uid, String viewpoint) throws SQLException {
        List<String> treeIds = new ArrayList<String>();
        String sql = "SELECT treeId FROM sw_chain_detail WHERE uid = ? AND viewpoint like ? ";
        Connection connection = dbConnectUtil.getConnection();
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setString(1, uid);
            preparedStatement.setString(2, "%" + viewpoint + "%");

            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                treeIds.add(resultSet.getString("treeId"));
            }
        } catch (Exception e) {
            logger.error("Failed to query treeIds for Viewpoint[{}]", viewpoint);
            throw new RuntimeException("Failed to query treeIds for " + viewpoint, e);
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
        return treeIds;
    }
}
