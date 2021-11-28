package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import org.apache.skywalking.oap.server.core.query.type.ProfileTask;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileTaskQueryDAO;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * {@link org.apache.skywalking.oap.server.core.profile.ProfileTaskRecord} is a stream
 */
public class BanyanDBProfileTaskQueryDAO implements IProfileTaskQueryDAO {
    @Override
    public List<ProfileTask> getTaskList(String serviceId, String endpointName, Long startTimeBucket, Long endTimeBucket, Integer limit) throws IOException {
        return Collections.emptyList();
    }

    @Override
    public ProfileTask getById(String id) throws IOException {
        return null;
    }
}
