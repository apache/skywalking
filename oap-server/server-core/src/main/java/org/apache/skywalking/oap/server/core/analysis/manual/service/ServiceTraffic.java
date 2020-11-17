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

package org.apache.skywalking.oap.server.core.analysis.manual.service;

import java.util.HashMap;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.MetricsExtension;
import org.apache.skywalking.oap.server.core.analysis.NodeType;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

import static org.apache.skywalking.oap.server.core.Const.DOUBLE_COLONS_SPLIT;

@Stream(name = ServiceTraffic.INDEX_NAME, scopeId = DefaultScopeDefine.SERVICE,
    builder = ServiceTraffic.Builder.class, processor = MetricsStreamProcessor.class)
@MetricsExtension(supportDownSampling = false, supportUpdate = false)
@EqualsAndHashCode(of = {
    "name",
    "nodeType"
})
public class ServiceTraffic extends Metrics {
    public static final String INDEX_NAME = "service_traffic";

    public static final String NAME = "name";
    public static final String NODE_TYPE = "node_type";
    public static final String GROUP = "service_group";

    @Setter
    @Getter
    @Column(columnName = NAME, matchQuery = true)
    private String name = Const.EMPTY_STRING;

    @Setter
    @Getter
    @Column(columnName = NODE_TYPE)
    private NodeType nodeType;

    @Setter
    @Getter
    @Column(columnName = GROUP)
    private String group;

    @Override
    public String id() {
        return IDManager.ServiceID.buildId(name, nodeType);
    }

    @Override
    public int remoteHashCode() {
        return this.hashCode();
    }

    @Override
    public void deserialize(final RemoteData remoteData) {
        setName(remoteData.getDataStrings(0));
        setNodeType(NodeType.valueOf(remoteData.getDataIntegers(0)));
        // Time bucket is not a part of persistent, but still is required in the first time insert.
        setTimeBucket(remoteData.getDataLongs(0));
    }

    @Override
    public RemoteData.Builder serialize() {
        final RemoteData.Builder builder = RemoteData.newBuilder();
        builder.addDataStrings(name);
        builder.addDataIntegers(nodeType.value());
        // Time bucket is not a part of persistent, but still is required in the first time insert.
        builder.addDataLongs(getTimeBucket());
        return builder;
    }

    public static class Builder implements StorageBuilder<ServiceTraffic> {

        @Override
        public ServiceTraffic map2Data(final Map<String, Object> dbMap) {
            ServiceTraffic serviceTraffic = new ServiceTraffic();
            serviceTraffic.setName((String) dbMap.get(NAME));
            serviceTraffic.setNodeType(NodeType.valueOf(((Number) dbMap.get(NODE_TYPE)).intValue()));
            serviceTraffic.setGroup((String) dbMap.get(GROUP));
            return serviceTraffic;
        }

        @Override
        public Map<String, Object> data2Map(final ServiceTraffic storageData) {
            final String serviceName = storageData.getName();
            if (NodeType.Normal.equals(storageData.getNodeType())) {
                int groupIdx = serviceName.indexOf(DOUBLE_COLONS_SPLIT);
                if (groupIdx > 0) {
                    storageData.setGroup(serviceName.substring(0, groupIdx));
                }
            }
            Map<String, Object> map = new HashMap<>();
            map.put(NAME, serviceName);
            map.put(NODE_TYPE, storageData.getNodeType().value());
            map.put(GROUP, storageData.getGroup());
            return map;
        }
    }

    @Override
    public void combine(final Metrics metrics) {

    }

    @Override
    public void calculate() {

    }

    @Override
    public Metrics toHour() {
        return null;
    }

    @Override
    public Metrics toDay() {
        return null;
    }
}

