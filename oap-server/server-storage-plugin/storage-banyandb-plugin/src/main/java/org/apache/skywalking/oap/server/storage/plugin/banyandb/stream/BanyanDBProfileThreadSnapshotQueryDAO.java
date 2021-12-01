package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import org.apache.skywalking.banyandb.v1.client.PairQueryCondition;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.profile.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.core.query.type.BasicTrace;
import org.apache.skywalking.oap.server.core.storage.AbstractDAO;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer.BasicTraceMapper;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer.ProfileThreadSnapshotRecordMapper;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer.RowEntityMapper;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer.SegmentRecordMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@link ProfileThreadSnapshotRecord} is a stream
 */
public class BanyanDBProfileThreadSnapshotQueryDAO extends AbstractDAO<BanyanDBStorageClient> implements IProfileThreadSnapshotQueryDAO {
    private static final RowEntityMapper<ProfileThreadSnapshotRecord> MAPPER = new ProfileThreadSnapshotRecordMapper();
    private static final RowEntityMapper<BasicTrace> BASIC_TRACE_MAPPER = new BasicTraceMapper();
    private static final RowEntityMapper<SegmentRecord> SEGMENT_RECORD_MAPPER = new SegmentRecordMapper();

    public BanyanDBProfileThreadSnapshotQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public List<BasicTrace> queryProfiledSegments(String taskId) throws IOException {
        final StreamQuery query = new StreamQuery(ProfileThreadSnapshotRecord.INDEX_NAME, MAPPER.searchableProjection());
        query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable", ProfileThreadSnapshotRecord.TASK_ID, taskId))
                .appendCondition(PairQueryCondition.LongQueryCondition.eq("searchable", ProfileThreadSnapshotRecord.SEQUENCE, 0L));
        StreamQueryResponse resp = getClient().query(query);

        final List<String> segmentIDs = new ArrayList<>(resp.size());
        resp.getElements().forEach(elem -> segmentIDs.add(MAPPER.map(elem).getSegmentId()));
        if (segmentIDs.isEmpty()) {
            return Collections.emptyList();
        }

        // TODO: support `IN` or `OR` logic operation in BanyanDB
        List<BasicTrace> basicTraces = new LinkedList<>();
        for (String segmentID : segmentIDs) {
            final StreamQuery traceQuery = new StreamQuery(SegmentRecord.INDEX_NAME, BASIC_TRACE_MAPPER.searchableProjection());
            traceQuery.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable", SegmentRecord.SEGMENT_ID, segmentID));
            StreamQueryResponse traceResponse = getClient().query(traceQuery);
            basicTraces.addAll(traceResponse.getElements().stream().map(BASIC_TRACE_MAPPER::map).collect(Collectors.toList()));
        }

        // TODO: Sort in DB with DESC
        basicTraces = basicTraces.stream()
                // comparing start_time
                .sorted(Comparator.comparing((Function<BasicTrace, Long>) basicTrace -> Long.parseLong(basicTrace.getStart()))
                        // and sort in reverse order
                        .reversed())
                .collect(Collectors.toList());
        return basicTraces;
    }

    @Override
    public int queryMinSequence(String segmentId, long start, long end) throws IOException {
        return querySequenceWithAgg(AggType.MIN, segmentId, start, end);
    }

    @Override
    public int queryMaxSequence(String segmentId, long start, long end) throws IOException {
        return querySequenceWithAgg(AggType.MAX, segmentId, start, end);
    }

    @Override
    public List<ProfileThreadSnapshotRecord> queryRecords(String segmentId, int minSequence, int maxSequence) throws IOException {
        final StreamQuery query = new StreamQuery(ProfileThreadSnapshotRecord.INDEX_NAME, MAPPER.searchableProjection());
        query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable", ProfileThreadSnapshotRecord.SEGMENT_ID, segmentId))
                .appendCondition(PairQueryCondition.LongQueryCondition.le("searchable", ProfileThreadSnapshotRecord.SEQUENCE, (long) maxSequence))
                .appendCondition(PairQueryCondition.LongQueryCondition.ge("searchable", ProfileThreadSnapshotRecord.SEQUENCE, (long) minSequence));
        query.setDataProjections(MAPPER.dataProjection());
        StreamQueryResponse resp = getClient().query(query);
        return resp.getElements().stream().map(MAPPER::map).collect(Collectors.toList());
    }

    @Override
    public SegmentRecord getProfiledSegment(String segmentId) throws IOException {
        final StreamQuery query = new StreamQuery(SegmentRecord.INDEX_NAME, SEGMENT_RECORD_MAPPER.searchableProjection());
        query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable", SegmentRecord.INDEX_NAME, segmentId));
        query.setDataProjections(SEGMENT_RECORD_MAPPER.dataProjection());
        StreamQueryResponse resp = getClient().query(query);
        return resp.getElements().stream().map(SEGMENT_RECORD_MAPPER::map).findFirst().orElse(null);
    }

    private int querySequenceWithAgg(AggType aggType, String segmentId, long start, long end) {
        final StreamQuery query = new StreamQuery(ProfileThreadSnapshotRecord.INDEX_NAME, MAPPER.searchableProjection());
        query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable", ProfileThreadSnapshotRecord.SEGMENT_ID, segmentId))
                .appendCondition(PairQueryCondition.LongQueryCondition.le("searchable", ProfileThreadSnapshotRecord.DUMP_TIME, end))
                .appendCondition(PairQueryCondition.LongQueryCondition.ge("searchable", ProfileThreadSnapshotRecord.DUMP_TIME, start));
        query.setDataProjections(MAPPER.dataProjection());

        StreamQueryResponse resp = getClient().query(query);
        List<ProfileThreadSnapshotRecord> records = resp.getElements().stream().map(MAPPER::map).collect(Collectors.toList());

        switch (aggType) {
            case MIN:
                int minValue = Integer.MAX_VALUE;
                for (final ProfileThreadSnapshotRecord record : records) {
                    int sequence = record.getSequence();
                    minValue = Math.min(minValue, sequence);
                }
                return minValue;
            case MAX:
                int maxValue = Integer.MIN_VALUE;
                for (ProfileThreadSnapshotRecord record : records) {
                    int sequence = record.getSequence();
                    maxValue = Math.max(maxValue, sequence);
                }
                return maxValue;
            default:
                throw new IllegalArgumentException("should not reach this line");
        }
    }

    enum AggType {
        MIN, MAX
    }
}