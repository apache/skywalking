package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import com.google.common.collect.ImmutableSet;
import org.apache.skywalking.oap.server.core.profiling.pprof.storage.PprofProfilingDataRecord;
import org.apache.skywalking.oap.server.core.storage.profiling.pprof.IPprofDataQueryDAO;
import java.util.Set;
import java.util.List;
import java.io.IOException;
import java.util.ArrayList;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBConverter;

public class BanyanDBPprofDataQueryDAO extends AbstractBanyanDBDAO implements IPprofDataQueryDAO {
    private static final Set<String> TAGS = ImmutableSet.of(
            PprofProfilingDataRecord.TASK_ID,
            PprofProfilingDataRecord.INSTANCE_ID,
            PprofProfilingDataRecord.EVENT_TYPE,
            PprofProfilingDataRecord.UPLOAD_TIME,
            PprofProfilingDataRecord.DATA_BINARY
    );

    public BanyanDBPprofDataQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public List<PprofProfilingDataRecord> getByTaskIdAndInstances(String taskId, List<String> instanceIds) throws IOException {
        if (StringUtil.isBlank(taskId)) {
            return new ArrayList<>();
        }
        StreamQueryResponse resp = query(false, PprofProfilingDataRecord.INDEX_NAME, TAGS,
                new QueryBuilder<StreamQuery>() {
                    @Override
                    protected void apply(StreamQuery query) {
                        query.and(eq(PprofProfilingDataRecord.TASK_ID, taskId));
                        if (CollectionUtils.isNotEmpty(instanceIds)) {
                            query.and(in(PprofProfilingDataRecord.INSTANCE_ID, instanceIds));
                        }
                    }
                });
        List<PprofProfilingDataRecord> records = new ArrayList<>(resp.size());
        for (final RowEntity entity : resp.getElements()) {
            records.add(buildProfilingDataRecord(entity));
        }

        return records;
    }

    private PprofProfilingDataRecord buildProfilingDataRecord(RowEntity entity) {
        final PprofProfilingDataRecord.Builder builder = new PprofProfilingDataRecord.Builder();
        BanyanDBConverter.StorageToStream storageToStream = new BanyanDBConverter.StorageToStream(PprofProfilingDataRecord.INDEX_NAME, entity);
        return builder.storage2Entity(storageToStream);
    }
}
