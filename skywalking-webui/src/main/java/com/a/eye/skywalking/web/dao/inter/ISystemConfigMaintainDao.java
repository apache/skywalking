package com.a.eye.skywalking.web.dao.inter;

import com.a.eye.skywalking.web.entity.SystemConfig;

import java.sql.SQLException;

public interface ISystemConfigMaintainDao {
    SystemConfig querySystemConfigByKey(String key) throws SQLException;
}
