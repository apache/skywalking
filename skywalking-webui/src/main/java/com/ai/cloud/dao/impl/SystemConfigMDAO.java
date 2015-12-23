package com.ai.cloud.dao.impl;

import com.ai.cloud.dao.inter.ISystemConfigMDAO;
import com.ai.cloud.vo.mvo.SystemConfigMVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;

@Repository
public class SystemConfigMDAO implements ISystemConfigMDAO {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public SystemConfigMVO querySystemConfigByKey(String key) {
        final SystemConfigMVO systemConfigMVO = new SystemConfigMVO();
        String sql = "SELECT config_id, conf_key, conf_value, val_type, val_desc from system_config where conf_key = ? and sts = ?";
        final Object[] params = new Object[]{key, "A"};
        jdbcTemplate.query(sql, params, new RowCallbackHandler() {
            @Override
            public void processRow(ResultSet resultSet) throws SQLException {
                systemConfigMVO.setConfigId(resultSet.getString("config_id"));
                systemConfigMVO.setConfKey(resultSet.getString("conf_key"));
                systemConfigMVO.setConfValue(resultSet.getString("conf_value"));
                systemConfigMVO.setValueType(resultSet.getString("val_type"));
                systemConfigMVO.setValueDesc(resultSet.getString("val_desc"));
            }
        });

        return systemConfigMVO;
    }
}
