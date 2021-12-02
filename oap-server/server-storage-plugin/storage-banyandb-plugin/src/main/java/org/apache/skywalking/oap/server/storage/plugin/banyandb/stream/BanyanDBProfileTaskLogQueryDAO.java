package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.oap.server.core.query.type.ProfileTaskLog;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileTaskLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer.ProfileTaskLogMapper;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer.RowEntityMapper;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link org.apache.skywalking.oap.server.core.profile.ProfileTaskLogRecord} is a stream
 */
public class BanyanDBProfileTaskLogQueryDAO extends AbstractBanyanDBDAO implements IProfileTaskLogQueryDAO {
    private static final RowEntityMapper<ProfileTaskLog> MAPPER = new ProfileTaskLogMapper();

    private final int queryMaxSize;

    public BanyanDBProfileTaskLogQueryDAO(BanyanDBStorageClient client, int queryMaxSize) {
        super(client);
        this.queryMaxSize = queryMaxSize;
    }

    @Override
    public List<ProfileTaskLog> getTaskLogList() throws IOException {
        return query(ProfileTaskLog.class, new QueryBuilder() {
            @Override
            public void apply(StreamQuery query) {
                query.setLimit(BanyanDBProfileTaskLogQueryDAO.this.queryMaxSize);
            }
        }).stream().sorted(Comparator.comparingLong(ProfileTaskLog::getOperationTime))
                .collect(Collectors.toList());
    }
}
