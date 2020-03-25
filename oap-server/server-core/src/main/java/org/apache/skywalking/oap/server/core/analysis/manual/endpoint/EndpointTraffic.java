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

package org.apache.skywalking.oap.server.core.analysis.manual.endpoint;

import com.google.common.base.Strings;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.ScopeDeclaration;
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.ENDPOINT_TRAFFIC;

@ScopeDeclaration(id = ENDPOINT_TRAFFIC, name = "EndpointTraffic")
@Stream(name = EndpointTraffic.INDEX_NAME, scopeId = DefaultScopeDefine.ENDPOINT_TRAFFIC, builder = EndpointTraffic.Builder.class, processor = MetricsStreamProcessor.class)
public class EndpointTraffic extends Metrics {

    public static final String INDEX_NAME = "endpoint_traffic";

    public static final String SERVICE_ID = "service_id";
    public static final String NAME = "name";
    public static final String DETECT_POINT = "detect_point";

    @Setter
    @Getter
    private String entityId;
    @Setter
    @Getter
    @Column(columnName = SERVICE_ID)
    private int serviceId;
    @Setter
    @Getter
    @Column(columnName = NAME, matchQuery = true)
    private String name = Const.EMPTY_STRING;
    @Setter
    @Getter
    @Column(columnName = DETECT_POINT)
    private int detectPoint;

    public static String buildId(int serviceId, String endpointName, int detectPoint) {
        return serviceId + Const.ID_SPLIT + endpointName + Const.ID_SPLIT + detectPoint;
    }

    /**
     * @param id in the storage of endpoint traffic
     * @return [serviceId, endpointName, detectPoint]
     */
    public static String[] splitID(String id) {
        final String[] strings = id.split(Const.ID_PARSER_SPLIT);
        if (strings.length != 3) {
            throw new UnexpectedException("Can't split endpoint id into 3 parts, " + id);
        }
        return strings;
    }

    @Override
    public String id() {
        return buildId(serviceId, name, detectPoint);
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + serviceId;
        result = 31 * result + name.hashCode();
        result = 31 * result + detectPoint;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;

        EndpointTraffic source = (EndpointTraffic) obj;
        if (serviceId != source.getServiceId())
            return false;
        if (!name.equals(source.getName()))
            return false;
        return detectPoint == source.getDetectPoint();
    }

    @Override
    public RemoteData.Builder serialize() {
        RemoteData.Builder remoteBuilder = RemoteData.newBuilder();
        remoteBuilder.addDataIntegers(serviceId);
        remoteBuilder.addDataIntegers(detectPoint);

        remoteBuilder.addDataLongs(getTimeBucket());

        remoteBuilder.addDataStrings(Strings.isNullOrEmpty(name) ? Const.EMPTY_STRING : name);
        return remoteBuilder;
    }

    @Override
    public void deserialize(RemoteData remoteData) {
        setServiceId(remoteData.getDataIntegers(0));
        setDetectPoint(remoteData.getDataIntegers(1));

        setTimeBucket(remoteData.getDataLongs(0));

        setName(remoteData.getDataStrings(0));
    }

    @Override
    public int remoteHashCode() {
        int result = 17;
        result = 31 * result + serviceId;
        result = 31 * result + name.hashCode();
        result = 31 * result + detectPoint;
        return result;
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

    @Override
    public Metrics toMonth() {
        return null;
    }

    public static class Builder implements StorageBuilder<EndpointTraffic> {

        @Override
        public EndpointTraffic map2Data(Map<String, Object> dbMap) {
            EndpointTraffic inventory = new EndpointTraffic();
            inventory.setServiceId(((Number) dbMap.get(SERVICE_ID)).intValue());
            inventory.setName((String) dbMap.get(NAME));
            inventory.setDetectPoint(((Number) dbMap.get(DETECT_POINT)).intValue());
            inventory.setTimeBucket(((Number) dbMap.get(TIME_BUCKET)).longValue());
            return inventory;
        }

        @Override
        public Map<String, Object> data2Map(EndpointTraffic storageData) {
            Map<String, Object> map = new HashMap<>();
            map.put(SERVICE_ID, storageData.getServiceId());
            map.put(NAME, storageData.getName());
            map.put(DETECT_POINT, storageData.getDetectPoint());
            map.put(TIME_BUCKET, storageData.getTimeBucket());
            return map;
        }
    }
}
