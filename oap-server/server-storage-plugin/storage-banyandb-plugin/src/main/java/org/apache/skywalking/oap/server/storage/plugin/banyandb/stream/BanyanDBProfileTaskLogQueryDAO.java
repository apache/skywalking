package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.oap.server.core.profile.ProfileTaskLogRecord;
import org.apache.skywalking.oap.server.core.query.type.ProfileTaskLog;
import org.apache.skywalking.oap.server.core.storage.AbstractDAO;
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
public class BanyanDBProfileTaskLogQueryDAO extends AbstractDAO<BanyanDBStorageClient> implements IProfileTaskLogQueryDAO {
    private static final RowEntityMapper<ProfileTaskLog> MAPPER = new ProfileTaskLogMapper();

    private final int queryMaxSize;

    public BanyanDBProfileTaskLogQueryDAO(BanyanDBStorageClient client, int queryMaxSize) {
        super(client);
        this.queryMaxSize = queryMaxSize;
    }

    @Override
    public List<ProfileTaskLog> getTaskLogList() throws IOException {
        final StreamQuery query = new StreamQuery(ProfileTaskLogRecord.INDEX_NAME, MAPPER.searchableProjection());
        query.setDataProjections(MAPPER.dataProjection());
        query.setLimit(this.queryMaxSize);

        StreamQueryResponse resp = getClient().query(query);
        return resp.getElements().stream()
                .map(MAPPER::map)
                .sorted(Comparator.comparingLong(ProfileTaskLog::getOperationTime))
                .collect(Collectors.toList());
    }
}
