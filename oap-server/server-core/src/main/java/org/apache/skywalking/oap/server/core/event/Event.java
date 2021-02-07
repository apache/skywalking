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

package org.apache.skywalking.oap.server.core.event;

import java.util.HashMap;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.analysis.MetricsExtension;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.source.ScopeDeclaration;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.EVENT;

@Getter
@Setter
@ScopeDeclaration(id = EVENT, name = "Event")
@Stream(name = Event.INDEX_NAME, scopeId = EVENT, builder = Event.Builder.class, processor = MetricsStreamProcessor.class)
@EqualsAndHashCode(
    callSuper = false,
    of = "uuid"
)
@MetricsExtension(supportDownSampling = false, supportUpdate = true)
public class Event extends Metrics {

    public static final String INDEX_NAME = "events";

    public static final String UUID = "uuid";

    public static final String SERVICE = "service";

    public static final String SERVICE_INSTANCE = "service_instance";

    public static final String ENDPOINT = "endpoint";

    public static final String NAME = "name";

    public static final String TYPE = "type";

    public static final String MESSAGE = "message";

    public static final String PARAMETERS = "parameters";

    public static final String START_TIME = "start_time";

    public static final String END_TIME = "end_time";

    @Override
    public String id() {
        return getUuid();
    }

    @Column(columnName = UUID)
    private String uuid;

    @Column(columnName = SERVICE)
    private String service;

    @Column(columnName = SERVICE_INSTANCE)
    private String serviceInstance;

    @Column(columnName = ENDPOINT)
    private String endpoint;

    @Column(columnName = NAME)
    private String name;

    @Column(columnName = TYPE)
    private String type;

    @Column(columnName = MESSAGE)
    private String message;

    @Column(columnName = PARAMETERS, storageOnly = true)
    private String parameters;

    @Column(columnName = START_TIME)
    private long startTime;

    @Column(columnName = END_TIME)
    private long endTime;

    @Override
    public boolean combine(final Metrics metrics) {
        final Event event = (Event) metrics;

        // Set time bucket only when it's never set.
        if (getTimeBucket() <= 0) {
            if (event.getStartTime() > 0) {
                setTimeBucket(TimeBucket.getMinuteTimeBucket(event.getStartTime()));
            } else if (event.getEndTime() > 0) {
                setTimeBucket(TimeBucket.getMinuteTimeBucket(event.getEndTime()));
            }
        }

        // Set start time only when it's never set, (`start` event may come after `end` event).
        if (getStartTime() <= 0 && event.getStartTime() > 0) {
            setStartTime(event.getStartTime());
        }

        if (event.getEndTime() > 0) {
            setEndTime(event.getEndTime());
        }

        if (StringUtil.isNotBlank(event.getType())) {
            setType(event.getType());
        }
        if (StringUtil.isNotBlank(event.getMessage())) {
            setType(event.getMessage());
        }
        if (StringUtil.isNotBlank(event.getParameters())) {
            setParameters(event.getParameters());
        }
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

    @Override
    public void deserialize(final RemoteData remoteData) {
        setUuid(remoteData.getDataStrings(0));
        setService(remoteData.getDataStrings(1));
        setServiceInstance(remoteData.getDataStrings(2));
        setEndpoint(remoteData.getDataStrings(3));
        setName(remoteData.getDataStrings(4));
        setType(remoteData.getDataStrings(5));
        setMessage(remoteData.getDataStrings(6));
        setParameters(remoteData.getDataStrings(7));

        setStartTime(remoteData.getDataLongs(0));
        setEndTime(remoteData.getDataLongs(1));
        setTimeBucket(remoteData.getDataLongs(2));
    }

    @Override
    public RemoteData.Builder serialize() {
        final RemoteData.Builder builder = RemoteData.newBuilder();

        builder.addDataStrings(getUuid());
        builder.addDataStrings(getService());
        builder.addDataStrings(getServiceInstance());
        builder.addDataStrings(getEndpoint());
        builder.addDataStrings(getName());
        builder.addDataStrings(getType());
        builder.addDataStrings(getMessage());
        builder.addDataStrings(getParameters());

        builder.addDataLongs(getStartTime());
        builder.addDataLongs(getEndTime());
        builder.addDataLongs(getTimeBucket());

        return builder;
    }

    @Override
    public int remoteHashCode() {
        return hashCode();
    }

    public static class Builder implements StorageHashMapBuilder<Event> {
        @Override
        public Map<String, Object> entity2Storage(Event storageData) {
            Map<String, Object> map = new HashMap<>();
            map.put(UUID, storageData.getUuid());
            map.put(SERVICE, storageData.getService());
            map.put(SERVICE_INSTANCE, storageData.getServiceInstance());
            map.put(ENDPOINT, storageData.getEndpoint());
            map.put(NAME, storageData.getName());
            map.put(TYPE, storageData.getType());
            map.put(MESSAGE, storageData.getMessage());
            map.put(PARAMETERS, storageData.getParameters());
            map.put(START_TIME, storageData.getStartTime());
            map.put(END_TIME, storageData.getEndTime());
            map.put(TIME_BUCKET, storageData.getTimeBucket());
            return map;
        }

        @Override
        public Event storage2Entity(Map<String, Object> dbMap) {
            Event record = new Event();
            record.setUuid((String) dbMap.get(UUID));
            record.setService((String) dbMap.get(SERVICE));
            record.setServiceInstance((String) dbMap.get(SERVICE_INSTANCE));
            record.setEndpoint((String) dbMap.get(ENDPOINT));
            record.setName((String) dbMap.get(NAME));
            record.setType((String) dbMap.get(TYPE));
            record.setMessage((String) dbMap.get(MESSAGE));
            record.setParameters((String) dbMap.get(PARAMETERS));
            record.setStartTime(((Number) dbMap.get(START_TIME)).longValue());
            record.setEndTime(((Number) dbMap.get(END_TIME)).longValue());
            record.setTimeBucket(((Number) dbMap.get(TIME_BUCKET)).longValue());
            return record;
        }
    }
}
