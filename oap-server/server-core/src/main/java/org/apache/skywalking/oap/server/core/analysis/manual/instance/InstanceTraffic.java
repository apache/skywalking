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

package org.apache.skywalking.oap.server.core.analysis.manual.instance;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.MetricsExtension;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.SERVICE_INSTANCE;

@Stream(name = InstanceTraffic.INDEX_NAME, scopeId = SERVICE_INSTANCE,
    builder = InstanceTraffic.Builder.class, processor = MetricsStreamProcessor.class)
@MetricsExtension(supportDownSampling = false, supportUpdate = true)
@EqualsAndHashCode(of = {
    "serviceId",
    "name"
})
public class InstanceTraffic extends Metrics {
    public static final String INDEX_NAME = "instance_traffic";
    public static final String SERVICE_ID = "service_id";
    public static final String NAME = "name";
    public static final String LAST_PING_TIME_BUCKET = "last_ping";
    public static final String PROPERTIES = "properties";

    private static final Gson GSON = new Gson();

    @Setter
    @Getter
    @Column(columnName = SERVICE_ID)
    private String serviceId;

    @Setter
    @Getter
    @Column(columnName = NAME, storageOnly = true)
    private String name;
    @Setter
    @Getter
    @Column(columnName = LAST_PING_TIME_BUCKET)
    private long lastPingTimestamp;
    @Setter
    @Getter
    @Column(columnName = PROPERTIES, storageOnly = true, length = 50000)
    private JsonObject properties;

    @Override
    public boolean combine(final Metrics metrics) {
        final InstanceTraffic instanceTraffic = (InstanceTraffic) metrics;
        this.lastPingTimestamp = instanceTraffic.getLastPingTimestamp();
        if (instanceTraffic.getProperties() != null && instanceTraffic.getProperties().size() > 0) {
            this.properties = instanceTraffic.getProperties();
        }
        /**
         * Keep the time bucket as the same time inserted.
         */
        if (this.getTimeBucket() > metrics.getTimeBucket()) {
            this.setTimeBucket(metrics.getTimeBucket());
        }
        return true;
    }

    @Override
    public int remoteHashCode() {
        return this.hashCode();
    }

    @Override
    public void deserialize(final RemoteData remoteData) {
        setServiceId(remoteData.getDataStrings(0));
        setName(remoteData.getDataStrings(1));
        final String propString = remoteData.getDataStrings(2);
        if (StringUtil.isNotEmpty(propString)) {
            setProperties(GSON.fromJson(propString, JsonObject.class));
        }
        setLastPingTimestamp(remoteData.getDataLongs(0));
        setTimeBucket(remoteData.getDataLongs(1));
    }

    @Override
    public RemoteData.Builder serialize() {
        final RemoteData.Builder builder = RemoteData.newBuilder();
        builder.addDataStrings(serviceId);
        builder.addDataStrings(name);
        if (properties == null) {
            builder.addDataStrings(Const.EMPTY_STRING);
        } else {
            builder.addDataStrings(GSON.toJson(properties));
        }
        builder.addDataLongs(lastPingTimestamp);
        builder.addDataLongs(getTimeBucket());
        return builder;
    }

    @Override
    protected String id0() {
        return IDManager.ServiceInstanceID.buildId(serviceId, name);
    }

    public static class Builder implements StorageHashMapBuilder<InstanceTraffic> {
        @Override
        public InstanceTraffic storage2Entity(final Map<String, Object> dbMap) {
            InstanceTraffic instanceTraffic = new InstanceTraffic();
            instanceTraffic.setServiceId((String) dbMap.get(SERVICE_ID));
            instanceTraffic.setName((String) dbMap.get(NAME));
            final String propString = (String) dbMap.get(PROPERTIES);
            if (StringUtil.isNotEmpty(propString)) {
                instanceTraffic.setProperties(GSON.fromJson(propString, JsonObject.class));
            }
            instanceTraffic.setLastPingTimestamp(((Number) dbMap.get(LAST_PING_TIME_BUCKET)).longValue());
            instanceTraffic.setTimeBucket(((Number) dbMap.get(TIME_BUCKET)).longValue());
            return instanceTraffic;
        }

        @Override
        public Map<String, Object> entity2Storage(final InstanceTraffic storageData) {
            Map<String, Object> map = new HashMap<>();
            map.put(SERVICE_ID, storageData.getServiceId());
            map.put(NAME, storageData.getName());
            if (storageData.getProperties() != null) {
                map.put(PROPERTIES, GSON.toJson(storageData.getProperties()));
            } else {
                map.put(PROPERTIES, Const.EMPTY_STRING);
            }
            map.put(LAST_PING_TIME_BUCKET, storageData.getLastPingTimestamp());
            map.put(TIME_BUCKET, storageData.getTimeBucket());
            return map;
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

    public static class PropertyUtil {
        public static final String LANGUAGE = "language";
        public static final String IPV4 = "ipv4";
        public static final String IPV4S = "ipv4s";
    }
}
