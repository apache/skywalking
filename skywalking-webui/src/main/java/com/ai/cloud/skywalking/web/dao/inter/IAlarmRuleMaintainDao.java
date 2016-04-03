package com.ai.cloud.skywalking.web.dao.inter;

import com.ai.cloud.skywalking.web.entity.AlarmRule;

import java.sql.SQLException;

public interface IAlarmRuleMaintainDao {
    AlarmRule queryGlobalAlarmRule(String userId) throws SQLException;

    void saveAlarmRule(AlarmRule rule) throws SQLException;

    void updateAlarmRule(AlarmRule alarmRule) throws SQLException;

    AlarmRule queryAlarmRule(String userId, String applicationId) throws SQLException;
}
