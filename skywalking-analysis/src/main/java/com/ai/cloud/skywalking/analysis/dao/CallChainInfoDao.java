package com.ai.cloud.skywalking.analysis.dao;

import com.ai.cloud.skywalking.analysis.config.Config;
import com.ai.cloud.skywalking.analysis.model.ChainNode;
import com.ai.cloud.skywalking.analysis.reduce.ChainDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.Map;

public class CallChainInfoDao {
    private static Logger logger = LoggerFactory.getLogger(CallChainInfoDao.class.getName());

    private static Connection connection;

    static {
        try {
            Class.forName(Config.MySql.driverClass);
            connection = DriverManager.getConnection(Config.MySql.url, Config.MySql.userName, Config.MySql.password);
        } catch (ClassNotFoundException e) {
            logger.error("Failed to find jdbc driver class[" + Config.MySql.driverClass + "]", e);
            System.exit(-1);
        } catch (SQLException e) {
            logger.error("Failed to connection database.", e);
            System.exit(-1);
        }
    }


    public static void saveChainDetail(ChainDetail chainDetail) throws SQLException {
        PreparedStatement preparedStatement = connection.prepareStatement("INSERT  INTO sw_chain_detail(cid,uid,traceLevelId,viewpoint,create_time)" +
                " VALUES(?,?,?,?,?)");
        for (ChainNode chainNode : chainDetail.getChainNodes()) {
            preparedStatement.setString(1, chainDetail.getChainToken());
            preparedStatement.setString(2, chainDetail.getUserId());
            preparedStatement.setString(3, chainNode.getTraceLevelId());
            preparedStatement.setString(4, chainNode.getViewPoint() + ":" + chainNode.getBusinessKey());
            preparedStatement.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
            preparedStatement.addBatch();
        }
        int[] result = preparedStatement.executeBatch();
        for (int i : result) {
            //TODO
        }
        preparedStatement.close();
        connection.commit();
    }

    public static void updateChainDetail(Map<String, Timestamp> updateChainInfo) throws SQLException {
        PreparedStatement preparedStatement = connection.prepareStatement("UPDATE sw_chain_detail SET update_time = ? WHERE cid = ?");
        for (Map.Entry<String, Timestamp> entry : updateChainInfo.entrySet()) {
            preparedStatement.setTimestamp(1, entry.getValue());
            preparedStatement.setString(2, entry.getKey());
            preparedStatement.addBatch();
        }
        int[] result = preparedStatement.executeBatch();
        for (int i : result) {
            //TODO
        }
        preparedStatement.close();
        connection.commit();
    }
}
