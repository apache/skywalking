package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import org.apache.skywalking.banyandb.v1.client.PairQueryCondition;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.profile.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.core.query.type.BasicTrace;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer.BasicTraceMapper;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer.ProfileThreadSnapshotRecordMapper;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer.RowEntityMapper;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer.SegmentRecordMapper;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@link ProfileThreadSnapshotRecord} is a stream
 */
public class BanyanDBProfileThreadSnapshotQueryDAO extends AbstractBanyanDBDAO implements IProfileThreadSnapshotQueryDAO {
    private static final RowEntityMapper<ProfileThreadSnapshotRecord> MAPPER = new ProfileThreadSnapshotRecordMapper();
    private static final RowEntityMapper<BasicTrace> BASIC_TRACE_MAPPER = new BasicTraceMapper();
    private static final RowEntityMapper<SegmentRecord> SEGMENT_RECORD_MAPPER = new SegmentRecordMapper();

    public BanyanDBProfileThreadSnapshotQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public List<BasicTrace> queryProfiledSegments(String taskId) throws IOException {
        List<ProfileThreadSnapshotRecord> resp = query(ProfileThreadSnapshotRecord.class, new QueryBuilder() {
            @Override
            public void apply(StreamQuery query) {
                query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable", ProfileThreadSnapshotRecord.TASK_ID, taskId))
                        .appendCondition(PairQueryCondition.LongQueryCondition.eq("searchable", ProfileThreadSnapshotRecord.SEQUENCE, 0L));
            }
        });

        if (resp.isEmpty()) {
            return Collections.emptyList();
        }

        final List<String> segmentIDs = resp.stream().map(ProfileThreadSnapshotRecord::getSegmentId).collect(Collectors.toList());

        // TODO: support `IN` or `OR` logic operation in BanyanDB
        List<BasicTrace> basicTraces = new LinkedList<>();
        for (String segmentID : segmentIDs) {
            List<BasicTrace> subSet = query(BasicTrace.class, new QueryBuilder() {
                @Override
                public void apply(StreamQuery traceQuery) {
                    traceQuery.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable", SegmentRecord.SEGMENT_ID, segmentID));
                }
            });
            basicTraces.addAll(subSet);
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
        return query(ProfileThreadSnapshotRecord.class, new QueryBuilder() {
            @Override
            public void apply(StreamQuery query) {
                query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable", ProfileThreadSnapshotRecord.SEGMENT_ID, segmentId))
                        .appendCondition(PairQueryCondition.LongQueryCondition.le("searchable", ProfileThreadSnapshotRecord.SEQUENCE, (long) maxSequence))
                        .appendCondition(PairQueryCondition.LongQueryCondition.ge("searchable", ProfileThreadSnapshotRecord.SEQUENCE, (long) minSequence));
            }
        });
    }

    @Override
    public SegmentRecord getProfiledSegment(String segmentId) throws IOException {
        return query(SegmentRecord.class, new QueryBuilder() {
            @Override
            public void apply(StreamQuery query) {
                query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable", SegmentRecord.INDEX_NAME, segmentId));
            }
        }).stream().findFirst().orElse(null);
    }

    private int querySequenceWithAgg(AggType aggType, String segmentId, long start, long end) {
        List<ProfileThreadSnapshotRecord> records = query(ProfileThreadSnapshotRecord.class, new QueryBuilder() {
            @Override
            public void apply(StreamQuery query) {
                query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable", ProfileThreadSnapshotRecord.SEGMENT_ID, segmentId))
                        .appendCondition(PairQueryCondition.LongQueryCondition.le("searchable", ProfileThreadSnapshotRecord.DUMP_TIME, end))
                        .appendCondition(PairQueryCondition.LongQueryCondition.ge("searchable", ProfileThreadSnapshotRecord.DUMP_TIME, start));
            }
        });

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