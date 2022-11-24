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

package org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.vservice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanLayer;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.oap.server.analyzer.provider.AnalyzerModuleConfig;
import org.apache.skywalking.oap.server.analyzer.provider.trace.DBLatencyThresholdsAndWatcher;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.SpanTags;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.DatabaseAccess;
import org.apache.skywalking.oap.server.core.source.DatabaseSlowStatement;
import org.apache.skywalking.oap.server.core.source.ServiceMeta;
import org.apache.skywalking.oap.server.core.source.Source;
import org.apache.skywalking.oap.server.library.util.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@Slf4j
@RequiredArgsConstructor
public class VirtualDatabaseProcessor implements VirtualServiceProcessor {

    private final NamingControl namingControl;

    private final AnalyzerModuleConfig config;

    private List<Source> recordList = new ArrayList<>();

    @Override
    public void prepareVSIfNecessary(SpanObject span, SegmentObject segmentObject) {
        if (span.getSpanLayer() != SpanLayer.Database) {
            return;
        }
        String peer = span.getPeer();
        long timeBucket = TimeBucket.getMinuteTimeBucket(span.getStartTime());
        String serviceName = namingControl.formatServiceName(peer);
        int latency = (int) (span.getEndTime() - span.getStartTime());
        recordList.add(toServiceMeta(serviceName, timeBucket));
        recordList.add(toDatabaseAccess(span, serviceName, timeBucket, latency));

        readStatementIfSlow(span.getTagsList(), latency).ifPresent(statement -> {
            DatabaseSlowStatement dbSlowStat = new DatabaseSlowStatement();
            dbSlowStat.setId(segmentObject.getTraceSegmentId() + "-" + span.getSpanId());
            dbSlowStat.setTraceId(segmentObject.getTraceId());
            dbSlowStat.setDatabaseServiceId(IDManager.ServiceID.buildId(serviceName, false));
            dbSlowStat.setStatement(statement);
            dbSlowStat.setLatency(latency);
            dbSlowStat.setTimeBucket(TimeBucket.getRecordTimeBucket(span.getStartTime()));
            dbSlowStat.setTimestamp(span.getStartTime());
            recordList.add(dbSlowStat);
        });
    }

    private Optional<String> readStatementIfSlow(List<KeyStringValuePair> tags, int latency) {
        String statement = null;
        boolean isSlowDBAccess = false;
        for (KeyStringValuePair tag : tags) {
            if (SpanTags.DB_STATEMENT.equals(tag.getKey())) {
                statement = StringUtil.cut(tag.getValue(), config.getMaxSlowSQLLength());
            } else if (SpanTags.DB_TYPE.equals(tag.getKey())) {
                String dbType = tag.getValue();
                DBLatencyThresholdsAndWatcher thresholds = config.getDbLatencyThresholdsAndWatcher();
                int threshold = thresholds.getThreshold(dbType);
                if (latency > threshold) {
                    isSlowDBAccess = true;
                }
            }
        }
        if (isSlowDBAccess) {
            return Optional.ofNullable(statement).filter(StringUtil::isNotBlank);
        }
        return Optional.empty();
    }

    private ServiceMeta toServiceMeta(String serviceName, Long timeBucket) {
        ServiceMeta service = new ServiceMeta();
        service.setName(serviceName);
        service.setLayer(Layer.VIRTUAL_DATABASE);
        service.setTimeBucket(timeBucket);
        return service;
    }

    private DatabaseAccess toDatabaseAccess(SpanObject span, String serviceName, long timeBucket, int latency) {
        DatabaseAccess databaseAccess = new DatabaseAccess();
        databaseAccess.setDatabaseTypeId(span.getComponentId());
        databaseAccess.setLatency(latency);
        databaseAccess.setName(serviceName);
        databaseAccess.setStatus(!span.getIsError());
        databaseAccess.setTimeBucket(timeBucket);
        return databaseAccess;
    }

    @Override
    public void emitTo(Consumer<Source> consumer) {
        recordList.forEach(consumer);
    }
}
