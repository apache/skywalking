package com.ai.cloud.skywalking.analysis.chainbuild;

import com.ai.cloud.skywalking.analysis.chainbuild.entity.CallChainDetailForMysql;
import com.ai.cloud.skywalking.analysis.chainbuild.po.ChainNode;
import com.ai.cloud.skywalking.analysis.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.Map;

public class DBCallChainInfoDao {
	private static Logger logger = LoggerFactory
			.getLogger(DBCallChainInfoDao.class.getName());

	private static Connection connection;

	static {
		try {
			Class.forName(Config.MySql.DRIVER_CLASS);
			connection = DriverManager.getConnection(Config.MySql.URL,
					Config.MySql.USERNAME, Config.MySql.PASSWORD);
		} catch (ClassNotFoundException e) {
			logger.error("Failed to searchRelationship jdbc driver class["
					+ Config.MySql.DRIVER_CLASS + "]", e);
			System.exit(-1);
		} catch (SQLException e) {
			logger.error("Failed to connection database.", e);
			System.exit(-1);
		}
	}

	public synchronized static void saveChainDetail(CallChainDetailForMysql callChainDetailForMysql)
			throws SQLException {
		PreparedStatement preparedStatement = null;
		try {
			preparedStatement = connection
					.prepareStatement("INSERT  INTO sw_chain_detail(treeId,uid,traceLevelId,viewpoint,create_time)"
							+ " VALUES(?,?,?,?,?)");
			for (ChainNode chainNode : callChainDetailForMysql.getChainNodes()) {
				preparedStatement.setString(1, callChainDetailForMysql.getTreeToken());
				preparedStatement.setString(2, callChainDetailForMysql.getUserId());
				preparedStatement.setString(3, chainNode.getTraceLevelId());
				preparedStatement.setString(4, chainNode.getViewPoint());
				preparedStatement.setTimestamp(5,
						new Timestamp(System.currentTimeMillis()));
				preparedStatement.addBatch();
			}
			int[] result = preparedStatement.executeBatch();
			for (int i : result) {
				if (i != 1) {
					logger.error("Failed to save chain detail ["
							+ callChainDetailForMysql.getChainToken() + "]");
				}
			}
		} finally {
			if (preparedStatement != null)
				preparedStatement.close();
		}
		connection.commit();
	}
}
