package com.ai.cloud.skywalking.web.dao.inter;

import com.ai.cloud.skywalking.web.dto.ApplicationInfo;
import com.ai.cloud.skywalking.web.entity.Application;

import java.sql.SQLException;
import java.util.List;

/**
 * Created by xin on 16-3-28.
 */
public interface IApplicationsMaintainDao {

    ApplicationInfo loadApplication(String applicationId, String uid) throws SQLException;

    void saveApplication(ApplicationInfo application) throws SQLException;

    void modifyApplication(Application application) throws SQLException;

    List<ApplicationInfo> queryAllApplications(String userId) throws SQLException;

    public void delApplication(String userId, String  applicationId) throws SQLException;
}
