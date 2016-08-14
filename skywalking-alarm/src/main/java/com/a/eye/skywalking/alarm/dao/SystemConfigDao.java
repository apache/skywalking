package com.a.eye.skywalking.alarm.dao;

import com.a.eye.skywalking.alarm.util.DBConnectUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SystemConfigDao {
    private static Logger logger = LogManager.getLogger(AlarmMessageDao.class);

    public static String getSystemConfig(String configId) throws SQLException {
    	
    	String result = "";
    	Connection connection = DBConnectUtil.getConnection();
    	
    	try {
    		 PreparedStatement ps = connection.prepareStatement(
    	                "SELECT system_config.conf_value FROM system_config WHERE system_config.sts = " +
    	                        "? AND system_config.config_id = ?");
    	        ps.setString(1, "A");
    	        ps.setString(2, configId);

    	        ResultSet resultSet = ps.executeQuery();
    	        resultSet.next();
    	        
    	        result = resultSet.getString("conf_value");			
		} catch (Exception e) {
			logger.error("Failed to find systemConfig.");
			throw e;
		} finally {
			if(connection != null) {
				connection.close();
			}
		}
        return result; 
    }
}
