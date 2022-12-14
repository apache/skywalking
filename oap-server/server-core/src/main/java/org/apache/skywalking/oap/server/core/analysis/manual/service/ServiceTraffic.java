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

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.MetricsExtension;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.ShardingAlgorithm;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ElasticSearch;
import org.apache.skywalking.oap.server.core.storage.annotation.SQLDatabase;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

import static org.apache.skywalking.oap.server.core.Const.DOUBLE_COLONS_SPLIT;

@Stream(name = ServiceTraffic.INDEX_NAME, scopeId = DefaultScopeDefine.SERVICE,
    builder = ServiceTraffic.Builder.class, processor = MetricsStreamProcessor.class)
@MetricsExtension(supportDownSampling = false, supportUpdate = false)
@EqualsAndHashCode(of = {
    "name",
    "layer"
})
@SQLDatabase.Sharding(shardingAlgorithm = ShardingAlgorithm.NO_SHARDING)
public class ServiceTraffic extends Metrics {
    public static final String INDEX_NAME = "service_traffic";

    public static final String NAME = "name";

    public static final String SHORT_NAME = "short_name";

    public static final String SERVICE_ID = "service_id";

    public static final String GROUP = "service_group";

    public static final String LAYER = "layer";

    @Setter
    @Getter
    @Column(columnName = NAME)
    @ElasticSearch.MatchQuery
    @ElasticSearch.Column(columnAlias = "service_traffic_name")
    private String name = Const.EMPTY_STRING;

    @Setter
    @Getter
    @Column(columnName = SHORT_NAME)
    private String shortName = Const.EMPTY_STRING;

    /**
     * `normal` Base64 encode(serviceName) + ".1" `un-normal` Base64 encode(serviceName) + ".0"
     */
    @Setter
    @Column(columnName = SERVICE_ID)
    private String serviceId;

    @Setter
    @Getter
    @Column(columnName = GROUP)
    private String group;

    @Setter
    @Getter
    @Column(columnName = LAYER)
    private Layer layer = Layer.UNDEFINED;

    /**
     * Primary key(id), to identify a service with different layers, a service could have more than one layer and be
     * saved as different records.
     *
     * @return Base64 encode(serviceName) + "." + layer.value
     */
    @Override
    protected StorageID id0() {
        return new StorageID().append(SERVICE_ID, getServiceId());
    }

    @Override
    public int remoteHashCode() {
        return this.hashCode();
    }

    @Override
    public void deserialize(final RemoteData remoteData) {
        setName(remoteData.getDataStrings(0));
        setLayer(Layer.valueOf(remoteData.getDataIntegers(0)));
        setTimeBucket(remoteData.getDataLongs(0));
    }

    @Override
    public RemoteData.Builder serialize() {
        final RemoteData.Builder builder = RemoteData.newBuilder();
        builder.addDataStrings(name);
        builder.addDataIntegers(layer.value());
        builder.addDataLongs(getTimeBucket());
        return builder;
    }

    public static class Builder implements StorageBuilder<ServiceTraffic> {
        @Override
        public ServiceTraffic storage2Entity(final Convert2Entity converter) {
            ServiceTraffic serviceTraffic = new ServiceTraffic();
            serviceTraffic.setName((String) converter.get(NAME));
            serviceTraffic.setShortName((String) converter.get(SHORT_NAME));
            serviceTraffic.setGroup((String) converter.get(GROUP));
            if (converter.get(LAYER) != null) {
                serviceTraffic.setLayer(Layer.valueOf(((Number) converter.get(LAYER)).intValue()));
            } else {
                serviceTraffic.setLayer(Layer.UNDEFINED);
            }
            // TIME_BUCKET column could be null in old implementation, which is fixed in 8.9.0
            if (converter.get(TIME_BUCKET) != null) {
                serviceTraffic.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            }
            return serviceTraffic;
        }

        @Override
        public void entity2Storage(final ServiceTraffic storageData, final Convert2Storage converter) {
            final String serviceName = storageData.getName();
            storageData.setShortName(serviceName);
            int groupIdx = serviceName.indexOf(DOUBLE_COLONS_SPLIT);
            if (groupIdx > 0) {
                storageData.setGroup(serviceName.substring(0, groupIdx));
                storageData.setShortName(serviceName.substring(groupIdx + 2));
            }
            converter.accept(NAME, serviceName);
            converter.accept(SHORT_NAME, storageData.getShortName());
            converter.accept(SERVICE_ID, storageData.getServiceId());
            converter.accept(GROUP, storageData.getGroup());
            Layer layer = storageData.getLayer();
            converter.accept(LAYER, layer != null ? layer.value() : Layer.UNDEFINED.value());
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
        }
    }

    @Override
    public boolean combine(final Metrics metrics) {
        return true;
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

    public String getServiceId() {
        if (serviceId == null) {
            serviceId = IDManager.ServiceID.buildId(name, layer.isNormal());
        }
        return serviceId;
    }
}

