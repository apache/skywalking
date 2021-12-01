package org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer;

import com.google.common.collect.ImmutableList;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.query.type.BasicTrace;

import java.util.Collections;
import java.util.List;

public class BasicTraceMapper implements RowEntityMapper<BasicTrace> {
    @Override
    public BasicTrace map(RowEntity row) {
        BasicTrace trace = new BasicTrace();
        trace.setSegmentId(row.getId());
        final List<TagAndValue<?>> searchable = row.getTagFamilies().get(0);
        trace.getTraceIds().add((String) searchable.get(0).getValue());
        trace.setError(((Long) searchable.get(1).getValue()).intValue() == 1);
        trace.getEndpointNames().add(IDManager.EndpointID.analysisId(
                (String) searchable.get(2).getValue()
        ).getEndpointName());
        trace.setDuration(((Long) searchable.get(3).getValue()).intValue());
        trace.setStart(String.valueOf(searchable.get(4).getValue()));
        return trace;
    }

    @Override
    public List<String> searchableProjection() {
        return ImmutableList.of("trace_id", "state", "endpoint_id", "duration", "start_time");
    }

    @Override
    public List<String> dataProjection() {
        return Collections.emptyList();
    }
}
