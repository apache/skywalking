package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.profile.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.core.query.type.BasicTrace;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileThreadSnapshotQueryDAO;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * {@link ProfileThreadSnapshotRecord} is a stream
 */
public class BanyanDBProfileThreadSnapshotQueryDAO implements IProfileThreadSnapshotQueryDAO {
    @Override
    public List<BasicTrace> queryProfiledSegments(String taskId) throws IOException {
        return Collections.emptyList();
    }

    @Override
    public int queryMinSequence(String segmentId, long start, long end) throws IOException {
        return 0;
    }

    @Override
    public int queryMaxSequence(String segmentId, long start, long end) throws IOException {
        return 0;
    }

    @Override
    public List<ProfileThreadSnapshotRecord> queryRecords(String segmentId, int minSequence, int maxSequence) throws IOException {
        return Collections.emptyList();
    }

    @Override
    public SegmentRecord getProfiledSegment(String segmentId) throws IOException {
        return null;
    }
}