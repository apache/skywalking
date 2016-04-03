package com.ai.cloud.skywalking.web.dao.impl;

import com.ai.cloud.skywalking.web.dao.inter.ISystemConfigMaintainDao;
import com.ai.cloud.skywalking.web.entity.SystemConfig;
import com.ai.cloud.skywalking.web.util.DBConnectUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@Repository
public class SystemConfigMaintainDao implements ISystemConfigMaintainDao {

    private Logger logger = LogManager.getLogger(SystemConfigMaintainDao.class);

    @Autowired
    private DBConnectUtil dbConnectUtil;

    @Override
    public SystemConfig querySystemConfigByKey(String key) throws SQLException {
        String sql = "SELECT config_id, conf_key, conf_value, val_type, val_desc from system_config where conf_key = ? and sts = ?";
        SystemConfig systemConfig = null;
        Connection connection = dbConnectUtil.getConnection();
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setString(1, key);
            preparedStatement.setString(2, "A");

            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                systemConfig = new SystemConfig();
                systemConfig.setConfigId(resultSet.getString("config_id"));
                systemConfig.setConfKey(resultSet.getString("conf_key"));
                systemConfig.setConfValue(resultSet.getString("conf_value"));
                systemConfig.setValueType(resultSet.getString("val_type"));
                systemConfig.setValueDesc(resultSet.getString("val_desc"));
            }
        } catch (Exception e) {
            logger.error("Failed to load the key[" + key + "] configuration", e);
            throw new RuntimeException("Failed to load key[" + key + "] configuration", e);
        } finally {
            if (connection != null)
                connection.close();
        }
        return systemConfig;
    }
}
