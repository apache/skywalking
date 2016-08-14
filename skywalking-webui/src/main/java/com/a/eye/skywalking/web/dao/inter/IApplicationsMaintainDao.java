package com.a.eye.skywalking.web.dao.inter;

import com.a.eye.skywalking.web.entity.Application;
import com.a.eye.skywalking.web.dto.ApplicationInfo;

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
