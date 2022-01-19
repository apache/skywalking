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
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.MetricsExtension;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

import static org.apache.logging.log4j.util.Base64Util.encode;
import static org.apache.skywalking.oap.server.core.Const.DOUBLE_COLONS_SPLIT;

@Stream(name = ServiceTraffic.INDEX_NAME, scopeId = DefaultScopeDefine.SERVICE,
    builder = ServiceTraffic.Builder.class, processor = MetricsStreamProcessor.class)
@MetricsExtension(supportDownSampling = false, supportUpdate = false)
@EqualsAndHashCode(of = {
    "name",
    "layer"
})
public class ServiceTraffic extends Metrics {
    public static final String INDEX_NAME = "service_traffic";

    public static final String NAME = "name";

    public static final String SHORT_NAME = "short_name";

    public static final String SERVICE_ID = "service_id";

    public static final String GROUP = "service_group";

    public static final String LAYER = "layer";

    @Setter
    @Getter
    @Column(columnName = NAME, matchQuery = true)
    private String name = Const.EMPTY_STRING;

    @Setter
    @Getter
    @Column(columnName = SHORT_NAME)
    private String shortName = Const.EMPTY_STRING;

    /**
     * `normal` Base64 encode(serviceName) + ".1"
     * `un-normal` Base64 encode(serviceName) + ".0"
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
     * The `normal` status represents this service is detected by an agent.
     * The `un-normal` service is conjectured by telemetry data collected from agents on/in the `normal` service.
     */
    @Setter
    @Getter
    private boolean isNormal = true;

    /**
     * Primary key(id), to identify a service with different layers, a service could have more than one layer and be
     * saved as different records.
     * @return Base64 encode(serviceName) + "." + layer.value
     */
    @Override
    protected String id0() {
        if (layer != null) {
            return encode(name) + Const.POINT + layer.value();
        } else {
            return encode(name) + Const.POINT + Layer.UNDEFINED.value();
        }
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

    public static class Builder implements StorageHashMapBuilder<ServiceTraffic> {

        @Override
        public ServiceTraffic storage2Entity(final Map<String, Object> dbMap) {
            ServiceTraffic serviceTraffic = new ServiceTraffic();
            serviceTraffic.setName((String) dbMap.get(NAME));
            serviceTraffic.setShortName((String) dbMap.get(SHORT_NAME));
            serviceTraffic.setGroup((String) dbMap.get(GROUP));
            if (dbMap.get(LAYER) != null) {
                serviceTraffic.setLayer(Layer.valueOf(((Number) dbMap.get(LAYER)).intValue()));
            } else {
                serviceTraffic.setLayer(Layer.UNDEFINED);
            }
            // TIME_BUCKET column could be null in old implementation, which is fixed in 8.9.0
            if (dbMap.containsKey(TIME_BUCKET)) {
                serviceTraffic.setTimeBucket(((Number) dbMap.get(TIME_BUCKET)).longValue());
            }
            return serviceTraffic;
        }

        @Override
        public Map<String, Object> entity2Storage(final ServiceTraffic storageData) {
            final String serviceName = storageData.getName();
            storageData.setShortName(serviceName);
            if (storageData.isNormal) {
                int groupIdx = serviceName.indexOf(DOUBLE_COLONS_SPLIT);
                if (groupIdx > 0) {
                    storageData.setGroup(serviceName.substring(0, groupIdx));
                    storageData.setShortName(serviceName.substring(groupIdx + 2));
                }
            }
            Map<String, Object> map = new HashMap<>();
            map.put(NAME, serviceName);
            map.put(SHORT_NAME, storageData.getShortName());
            map.put(SERVICE_ID, storageData.getServiceId());
            map.put(GROUP, storageData.getGroup());
            Layer layer = storageData.getLayer();
            map.put(LAYER, layer != null ? layer.value() : Layer.UNDEFINED.value());
            map.put(TIME_BUCKET, storageData.getTimeBucket());
            return map;
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
            serviceId = IDManager.ServiceID.buildId(name, isNormal);
        }
        return serviceId;
    }
}

