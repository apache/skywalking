package com.ai.cloud.skywalking.analysis.dao;

import com.ai.cloud.skywalking.analysis.config.Config;
import com.ai.cloud.skywalking.analysis.model.ChainInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

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

    public static boolean existCallChainInfo(String cid, String userId) throws SQLException {
        final String sql = "SELECT COUNT(cid) as TOTAL_SIZE FROM sw_chain_info WHERE cid = ? AND uid = ?";
        PreparedStatement ps = connection.prepareStatement(sql);
        ps.setString(1, cid);
        ps.setString(2, userId);
        ResultSet resultSet = ps.executeQuery();
        resultSet.next();
        return resultSet.getInt("TOTAL_SIZE") > 0 ? true : false;
    }

}
