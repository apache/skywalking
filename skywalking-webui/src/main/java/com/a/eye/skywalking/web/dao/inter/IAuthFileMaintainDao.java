package com.a.eye.skywalking.web.dao.inter;

import java.sql.SQLException;
import java.util.Properties;

/**
 * Created by xin on 16-3-29.
 */
public interface IAuthFileMaintainDao {
    Properties queryAuthKeysToProperties(String authType) throws SQLException;
}
