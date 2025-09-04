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
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ElasticSearch;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.util.StringUtil;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.SERVICE_INSTANCE;

@Stream(name = InstanceTraffic.INDEX_NAME, scopeId = SERVICE_INSTANCE,
    builder = InstanceTraffic.Builder.class, processor = MetricsStreamProcessor.class)
@MetricsExtension(supportDownSampling = false, supportUpdate = true)
@EqualsAndHashCode(of = {
    "serviceId",
    "name"
})
@BanyanDB.IndexMode
public class InstanceTraffic extends Metrics {
    public static final String INDEX_NAME = "instance_traffic";
    public static final String SERVICE_ID = "service_id";
    public static final String NAME = "instance_traffic_name";
    public static final String LAST_PING_TIME_BUCKET = "last_ping";
    public static final String PROPERTIES = "properties";

    private static final Gson GSON = new Gson();

    @Setter
    @Getter
    @Column(name = SERVICE_ID)
    private String serviceId;

    @Setter
    @Getter
    @Column(name = NAME, storageOnly = true)
    @ElasticSearch.Column(legacyName = "name")
    private String name;

    @Setter
    @Getter
    @Column(name = LAST_PING_TIME_BUCKET)
    private long lastPingTimestamp;

    @Setter
    @Getter
    @Column(name = PROPERTIES, storageOnly = true, length = 50000)
    private JsonObject properties;

    @Override
    public boolean combine(final Metrics metrics) {
        final InstanceTraffic instanceTraffic = (InstanceTraffic) metrics;
        this.lastPingTimestamp = instanceTraffic.getLastPingTimestamp();
        if (instanceTraffic.getProperties() != null && instanceTraffic.getProperties().size() > 0) {
            if (this.properties == null) {
                this.properties = new JsonObject();
            }
            instanceTraffic.getProperties().entrySet().forEach(it -> this.properties.add(it.getKey(), it.getValue()));
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
    protected StorageID id0() {
        return new StorageID()
            .append(IDManager.ServiceInstanceID.buildId(serviceId, name));
    }

    public static class Builder implements StorageBuilder<InstanceTraffic> {
        @Override
        public InstanceTraffic storage2Entity(final Convert2Entity converter) {
            InstanceTraffic instanceTraffic = new InstanceTraffic();
            instanceTraffic.setServiceId((String) converter.get(SERVICE_ID));
            instanceTraffic.setName((String) converter.get(NAME));
            final String propString = (String) converter.get(PROPERTIES);
            if (StringUtil.isNotEmpty(propString)) {
                instanceTraffic.setProperties(GSON.fromJson(propString, JsonObject.class));
            }
            instanceTraffic.setLastPingTimestamp(((Number) converter.get(LAST_PING_TIME_BUCKET)).longValue());
            instanceTraffic.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            return instanceTraffic;
        }

        @Override
        public void entity2Storage(final InstanceTraffic storageData, final Convert2Storage converter) {
            converter.accept(SERVICE_ID, storageData.getServiceId());
            converter.accept(NAME, storageData.getName());
            if (storageData.getProperties() != null) {
                converter.accept(PROPERTIES, GSON.toJson(storageData.getProperties()));
            } else {
                converter.accept(PROPERTIES, Const.EMPTY_STRING);
            }
            converter.accept(LAST_PING_TIME_BUCKET, storageData.getLastPingTimestamp());
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

    public static class PropertyUtil {
        /**
         * `namespace` and `pod` are key properties that help "on demand Pod logs"
         * to locate the corresponding Pod in Kubernetes, when language agent is
         * registering a new service instance that is supposed to work in terms of
         * "on demand Pod logs", the agent should also fill in these 2 properties.
         *
         * @since 9.1.0
         */
        public static final String NAMESPACE = "namespace";
        public static final String POD = "pod";

        public static final String LANGUAGE = "language";
        public static final String IPV4 = "ipv4";
        public static final String IPV4S = "ipv4s";
    }
}
