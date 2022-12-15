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

package org.apache.skywalking.oap.server.core.analysis.metrics;

import com.google.common.base.Strings;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.MetricsExtension;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.source.ScopeDeclaration;
import org.apache.skywalking.oap.server.core.storage.ShardingAlgorithm;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.SQLDatabase;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.EVENT;
import static org.apache.skywalking.oap.server.library.util.StringUtil.isNotBlank;

@Getter
@Setter
@ScopeDeclaration(id = EVENT, name = "Event")
@Stream(name = Event.INDEX_NAME, scopeId = EVENT, builder = Event.Builder.class, processor = MetricsStreamProcessor.class)
@MetricsExtension(supportDownSampling = false, supportUpdate = true)
@EqualsAndHashCode(
    callSuper = false,
    of = "uuid"
)
@SQLDatabase.Sharding(shardingAlgorithm = ShardingAlgorithm.NO_SHARDING)
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

    public static final String LAYER = "layer";

    private static final int PARAMETER_MAX_LENGTH = 2000;

    @Override
    protected StorageID id0() {
        return new StorageID().append(UUID, getUuid());
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

    @Column(columnName = PARAMETERS, storageOnly = true, length = PARAMETER_MAX_LENGTH)
    private String parameters;

    @Column(columnName = START_TIME)
    private long startTime;

    @Column(columnName = END_TIME)
    private long endTime;

    @Column(columnName = LAYER)
    private Layer layer;

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

        if (isNotBlank(event.getType())) {
            setType(event.getType());
        }
        if (isNotBlank(event.getMessage())) {
            setMessage(event.getMessage());
        }
        if (isNotBlank(event.getParameters())) {
            setParameters(event.getParameters());
        }
        return true;
    }

    /**
     * @since 9.0.0 Limit the length of {@link #parameters}
     */
    public void setParameters(String parameters) {
        this.parameters = parameters == null || parameters.length() <= PARAMETER_MAX_LENGTH ?
            parameters : parameters.substring(0, PARAMETER_MAX_LENGTH);
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

        setLayer(Layer.valueOf(remoteData.getDataIntegers(0)));
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
        builder.addDataStrings(Strings.nullToEmpty(getParameters()));

        builder.addDataLongs(getStartTime());
        builder.addDataLongs(getEndTime());
        builder.addDataLongs(getTimeBucket());

        builder.addDataIntegers(getLayer().value());

        return builder;
    }

    @Override
    public int remoteHashCode() {
        return hashCode();
    }

    public static class Builder implements StorageBuilder<Event> {
        @Override
        public Event storage2Entity(final Convert2Entity converter) {
            Event record = new Event();
            record.setUuid((String) converter.get(UUID));
            record.setService((String) converter.get(SERVICE));
            record.setServiceInstance((String) converter.get(SERVICE_INSTANCE));
            record.setEndpoint((String) converter.get(ENDPOINT));
            record.setName((String) converter.get(NAME));
            record.setType((String) converter.get(TYPE));
            record.setMessage((String) converter.get(MESSAGE));
            record.setParameters((String) converter.get(PARAMETERS));
            record.setStartTime(((Number) converter.get(START_TIME)).longValue());
            record.setEndTime(((Number) converter.get(END_TIME)).longValue());
            record.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            if (converter.get(LAYER) != null) {
                record.setLayer(Layer.valueOf(((Number) converter.get(LAYER)).intValue()));
            }
            return record;
        }

        @Override
        public void entity2Storage(final Event storageData, final Convert2Storage converter) {
            converter.accept(UUID, storageData.getUuid());
            converter.accept(SERVICE, storageData.getService());
            converter.accept(SERVICE_INSTANCE, storageData.getServiceInstance());
            converter.accept(ENDPOINT, storageData.getEndpoint());
            converter.accept(NAME, storageData.getName());
            converter.accept(TYPE, storageData.getType());
            converter.accept(MESSAGE, storageData.getMessage());
            converter.accept(PARAMETERS, storageData.getParameters());
            converter.accept(START_TIME, storageData.getStartTime());
            converter.accept(END_TIME, storageData.getEndTime());
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
            Layer layer = storageData.getLayer();
            converter.accept(LAYER, layer != null ? layer.value() : Layer.UNDEFINED.value());
        }
    }
}
