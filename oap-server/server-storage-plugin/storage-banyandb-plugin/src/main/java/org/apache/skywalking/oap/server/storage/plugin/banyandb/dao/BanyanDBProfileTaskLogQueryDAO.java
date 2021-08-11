package org.apache.skywalking.oap.server.storage.plugin.banyandb.dao;

import org.apache.skywalking.oap.server.core.query.type.ProfileTaskLog;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileTaskLogQueryDAO;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class BanyanDBProfileTaskLogQueryDAO implements IProfileTaskLogQueryDAO {
    @Override
    public List<ProfileTaskLog> getTaskLogList() throws IOException {
        return Collections.emptyList();
    }
}
