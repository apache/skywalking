/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer;

import com.google.common.collect.ImmutableList;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.query.type.BasicTrace;

import java.util.List;

public class BasicTraceDeserializer extends AbstractBanyanDBDeserializer<BasicTrace> {
    public BasicTraceDeserializer() {
        super(SegmentRecord.INDEX_NAME, ImmutableList.of("trace_id", "state", "endpoint_id", "duration", "start_time"));
    }

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
}
