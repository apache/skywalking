package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import org.apache.skywalking.banyandb.v1.client.PairQueryCondition;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.profile.ProfileTaskRecord;
import org.apache.skywalking.oap.server.core.query.type.ProfileTask;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer.ProfileTaskMapper;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer.RowEntityMapper;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * {@link org.apache.skywalking.oap.server.core.profile.ProfileTaskRecord} is a stream
 */
public class BanyanDBProfileTaskQueryDAO extends AbstractBanyanDBDAO implements IProfileTaskQueryDAO {
    private static final RowEntityMapper<ProfileTask> MAPPER = new ProfileTaskMapper();

    public BanyanDBProfileTaskQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public List<ProfileTask> getTaskList(String serviceId, String endpointName, Long startTimeBucket, Long endTimeBucket, Integer limit) throws IOException {
        return query(ProfileTask.class, new QueryBuilder() {
            @Override
            public void apply(StreamQuery query) {
                if (StringUtil.isNotEmpty(serviceId)) {
                    query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable",
                            ProfileTaskRecord.SERVICE_ID, serviceId));
                }

                if (StringUtil.isNotEmpty(endpointName)) {
                    query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable",
                            ProfileTaskRecord.ENDPOINT_NAME, endpointName));
                }

                if (Objects.nonNull(startTimeBucket)) {
                    query.appendCondition(PairQueryCondition.LongQueryCondition.ge("searchable",
                            ProfileTaskRecord.START_TIME, TimeBucket.getTimestamp(startTimeBucket)));
                }

                if (Objects.nonNull(endTimeBucket)) {
                    query.appendCondition(PairQueryCondition.LongQueryCondition.le("searchable",
                            ProfileTaskRecord.START_TIME, TimeBucket.getTimestamp(endTimeBucket)));
                }

                if (Objects.nonNull(limit)) {
                    query.setLimit(limit);
                }

                query.setOrderBy(new StreamQuery.OrderBy(ProfileTaskRecord.START_TIME, StreamQuery.OrderBy.Type.DESC));
            }
        });
    }

    @Override
    public ProfileTask getById(String id) throws IOException {
        if (StringUtil.isEmpty(id)) {
            return null;
        }

        return query(ProfileTask.class, new QueryBuilder() {
            @Override
            public void apply(StreamQuery query) {
                query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable", ProfileTaskMapper.ID, id));
                query.setLimit(1);
            }
        }).stream().findAny().orElse(null);
    }
}
