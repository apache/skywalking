package com.ai.cloud.skywalking.web.dao.impl;

import com.ai.cloud.skywalking.web.dao.inter.IAuthFileMaintainDao;
import com.ai.cloud.skywalking.web.util.DBConnectUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

@Repository
public class AuthFileMaintainDao implements IAuthFileMaintainDao {

    @Autowired
    private DBConnectUtil dbConnectUtil;

    @Override
    public Properties queryAuthKeysToProperties(String authType) throws SQLException {
        final Properties properties = new Properties();
        String sql = "select auth_file_config.key, auth_file_config.value" + authType + " from auth_file_config where auth_file_config.sts = ? ";
        Connection connection = dbConnectUtil.getConnection();
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setString(1, "A");
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                properties.setProperty(resultSet.getString("key"), resultSet.getString("value" + authType));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to query auth key by authType[" + authType + "]", e);
        } finally {
            if (connection != null)
                connection.close();
        }
        return properties;
    }
}
