package com.ai.cloud.dao.impl;

import com.ai.cloud.dao.inter.IAuthConfigMDAO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

@Repository
public class AuthConfigMDAO implements IAuthConfigMDAO {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static Logger logger = LogManager.getLogger(AuthConfigMDAO.class);

    @Override
    public Properties queryAllAuthConfig(final String authType) {
        final Properties properties = new Properties();
        String sql = "select auth_file_config.key, auth_file_config.value" + authType + " from auth_file_config where auth_file_config.sts = ? ";
        final Object[] params = new Object[]{"A"};
        jdbcTemplate.query(sql, params, new RowCallbackHandler() {
            @Override
            public void processRow(ResultSet resultSet) throws SQLException {
                properties.setProperty(resultSet.getString("key"), resultSet.getString("value" + authType));
            }
        });
        return properties;
    }
}
