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

package org.apache.skywalking.oap.server.core.analysis.manual.process;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.MetricsExtension;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.storage.ShardingAlgorithm;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.SQLDatabase;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.util.StringUtil;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.PROCESS;

@Stream(name = ProcessTraffic.INDEX_NAME, scopeId = PROCESS,
    builder = ProcessTraffic.Builder.class, processor = MetricsStreamProcessor.class)
@MetricsExtension(supportDownSampling = false, supportUpdate = true)
@EqualsAndHashCode(of = {
    "instanceId",
    "name",
})
@SQLDatabase.Sharding(shardingAlgorithm = ShardingAlgorithm.NO_SHARDING)
@BanyanDB.StoreIDAsTag
public class ProcessTraffic extends Metrics {
    public static final String INDEX_NAME = "process_traffic";
    public static final String SERVICE_ID = "service_id";
    public static final String INSTANCE_ID = "instance_id";
    public static final String NAME = "name";
    public static final String AGENT_ID = "agent_id";
    public static final String PROPERTIES = "properties";
    public static final String LAST_PING_TIME_BUCKET = "last_ping";
    public static final String DETECT_TYPE = "detect_type";
    public static final String LABELS_JSON = "labels_json";
    public static final String PROFILING_SUPPORT_STATUS = "profiling_support_status";

    private static final Gson GSON = new Gson();

    @Setter
    @Getter
    @Column(columnName = SERVICE_ID)
    private String serviceId;

    @Setter
    @Getter
    @Column(columnName = INSTANCE_ID, length = 600)
    @BanyanDB.SeriesID(index = 0)
    private String instanceId;

    @Getter
    @Setter
    private String processId;

    @Setter
    @Getter
    @Column(columnName = NAME, length = 500)
    @BanyanDB.SeriesID(index = 1)
    private String name;

    @Setter
    @Getter
    @Column(columnName = LAST_PING_TIME_BUCKET)
    private long lastPingTimestamp;

    @Setter
    @Getter
    @Column(columnName = DETECT_TYPE)
    private int detectType = ProcessDetectType.UNDEFINED.value();

    @Setter
    @Getter
    @Column(columnName = AGENT_ID, length = 500)
    private String agentId;

    @Setter
    @Getter
    @Column(columnName = PROPERTIES, storageOnly = true, length = 50000)
    private JsonObject properties;

    @Setter
    @Getter
    @Column(columnName = LABELS_JSON, storageOnly = true, length = 500)
    private String labelsJson;

    /**
     * Is Support eBPF Profiling, 1 means support, otherwise means not support
     */
    @Setter
    @Getter
    @Column(columnName = PROFILING_SUPPORT_STATUS)
    private int profilingSupportStatus;

    @Override
    public boolean combine(Metrics metrics) {
        final ProcessTraffic processTraffic = (ProcessTraffic) metrics;
        this.lastPingTimestamp = processTraffic.getLastPingTimestamp();
        if (StringUtil.isNotBlank(processTraffic.getAgentId())) {
            this.agentId = processTraffic.getAgentId();
        }
        if (this.properties == null) {
            this.properties = processTraffic.getProperties();
        } else if (processTraffic.getProperties() != null) {
            for (Map.Entry<String, JsonElement> e : processTraffic.getProperties().entrySet()) {
                this.properties.add(e.getKey(), e.getValue());
            }
        }
        if (processTraffic.getDetectType() > 0) {
            this.detectType = processTraffic.getDetectType();
        }
        if (StringUtil.isNotEmpty(processTraffic.getLabelsJson())) {
            this.labelsJson = processTraffic.getLabelsJson();
        }
        return true;
    }

    @Override
    public int remoteHashCode() {
        return this.hashCode();
    }

    @Override
    public void deserialize(RemoteData remoteData) {
        setServiceId(remoteData.getDataStrings(0));
        setInstanceId(remoteData.getDataStrings(1));
        setName(remoteData.getDataStrings(2));
        setAgentId(remoteData.getDataStrings(3));
        final String propString = remoteData.getDataStrings(4);
        if (StringUtil.isNotEmpty(propString)) {
            setProperties(GSON.fromJson(propString, JsonObject.class));
        }
        setLabelsJson(remoteData.getDataStrings(5));
        setLastPingTimestamp(remoteData.getDataLongs(0));
        setDetectType(remoteData.getDataIntegers(0));
        setProfilingSupportStatus(remoteData.getDataIntegers(1));
        setTimeBucket(remoteData.getDataLongs(1));
    }

    @Override
    public RemoteData.Builder serialize() {
        final RemoteData.Builder builder = RemoteData.newBuilder();
        builder.addDataStrings(serviceId);
        builder.addDataStrings(instanceId);
        builder.addDataStrings(name);
        builder.addDataStrings(agentId);
        if (properties == null) {
            builder.addDataStrings(Const.EMPTY_STRING);
        } else {
            builder.addDataStrings(GSON.toJson(properties));
        }
        builder.addDataStrings(labelsJson);
        builder.addDataLongs(lastPingTimestamp);
        builder.addDataIntegers(detectType);
        builder.addDataIntegers(profilingSupportStatus);
        builder.addDataLongs(getTimeBucket());
        return builder;
    }

    @Override
    protected StorageID id0() {
        if (processId == null) {
            processId = IDManager.ProcessID.buildId(instanceId, name);
        }
        return new StorageID().appendMutant(new String[] {
            INSTANCE_ID,
            NAME
        }, processId);
    }

    public static class Builder implements StorageBuilder<ProcessTraffic> {
        @Override
        public ProcessTraffic storage2Entity(final Convert2Entity converter) {
            final ProcessTraffic processTraffic = new ProcessTraffic();
            processTraffic.setServiceId((String) converter.get(SERVICE_ID));
            processTraffic.setInstanceId((String) converter.get(INSTANCE_ID));
            processTraffic.setName((String) converter.get(NAME));
            processTraffic.setAgentId((String) converter.get(AGENT_ID));
            final String propString = (String) converter.get(PROPERTIES);
            if (StringUtil.isNotEmpty(propString)) {
                processTraffic.setProperties(GSON.fromJson(propString, JsonObject.class));
            }
            processTraffic.setLabelsJson((String) converter.get(LABELS_JSON));
            processTraffic.setLastPingTimestamp(((Number) converter.get(LAST_PING_TIME_BUCKET)).longValue());
            processTraffic.setDetectType(((Number) converter.get(DETECT_TYPE)).intValue());
            processTraffic.setProfilingSupportStatus(((Number) converter.get(PROFILING_SUPPORT_STATUS)).intValue());
            processTraffic.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            return processTraffic;
        }

        @Override
        public void entity2Storage(final ProcessTraffic storageData, final Convert2Storage converter) {
            converter.accept(SERVICE_ID, storageData.getServiceId());
            converter.accept(INSTANCE_ID, storageData.getInstanceId());
            converter.accept(NAME, storageData.getName());
            converter.accept(AGENT_ID, storageData.getAgentId());
            if (storageData.getProperties() != null) {
                converter.accept(PROPERTIES, GSON.toJson(storageData.getProperties()));
            } else {
                converter.accept(PROPERTIES, Const.EMPTY_STRING);
            }
            converter.accept(LABELS_JSON, storageData.getLabelsJson());
            converter.accept(LAST_PING_TIME_BUCKET, storageData.getLastPingTimestamp());
            converter.accept(DETECT_TYPE, storageData.getDetectType());
            converter.accept(PROFILING_SUPPORT_STATUS, storageData.getProfilingSupportStatus());
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
        }
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
