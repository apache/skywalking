package com.ai.cloud.skywalking.web.dao.inter;

import com.ai.cloud.skywalking.web.entity.SystemConfig;

import java.sql.SQLException;

public interface ISystemConfigMaintainDao {
    SystemConfig querySystemConfigByKey(String key) throws SQLException;
}
