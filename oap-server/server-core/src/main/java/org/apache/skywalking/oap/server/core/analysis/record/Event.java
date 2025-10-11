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

package org.apache.skywalking.oap.server.core.analysis.record;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.source.ScopeDeclaration;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ElasticSearch;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.EVENT;

@Getter
@Setter
@ScopeDeclaration(id = EVENT, name = "Event")
@Stream(name = Event.INDEX_NAME, scopeId = EVENT, builder = Event.Builder.class, processor = RecordStreamProcessor.class)
@BanyanDB.TimestampColumn(Event.TIMESTAMP)
@BanyanDB.Group(streamGroup = BanyanDB.StreamGroup.RECORDS)
public class Event extends Record {

    public static final String INDEX_NAME = "event";

    public static final String UUID = "uuid";

    public static final String SERVICE = "service";

    public static final String SERVICE_INSTANCE = "service_instance";

    public static final String ENDPOINT = "endpoint";

    public static final String NAME = "event_name";

    public static final String TYPE = "type";

    public static final String MESSAGE = "message";

    public static final String PARAMETERS = "parameters";

    public static final String START_TIME = "start_time";

    public static final String END_TIME = "end_time";

    public static final String LAYER = "layer";

    private static final int PARAMETER_MAX_LENGTH = 4000;

    public static final String TIMESTAMP = "timestamp";

    @Column(name = UUID)
    @BanyanDB.SeriesID(index = 0)
    private String uuid;

    @Column(name = SERVICE)
    private String service;

    @Column(name = SERVICE_INSTANCE)
    private String serviceInstance;

    @Column(name = ENDPOINT)
    private String endpoint;

    @Column(name = NAME)
    private String name;

    @Column(name = TYPE)
    private String type;

    @Column(name = MESSAGE, storageOnly = true, length = 2000)
    private String message;

    @Column(name = PARAMETERS, storageOnly = true, length = PARAMETER_MAX_LENGTH)
    private String parameters;

    @ElasticSearch.EnableDocValues
    @Column(name = START_TIME)
    private long startTime;

    @Column(name = END_TIME)
    private long endTime;

    @Column(name = LAYER)
    private Layer layer;

    @Setter
    @Getter
    @ElasticSearch.EnableDocValues
    @Column(name = TIMESTAMP)
    private long timestamp;

    @Override
    public StorageID id() {
        return new StorageID().append(TIME_BUCKET, getTimeBucket())
                .append(UUID, uuid);
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
            record.setTimestamp(((Number) converter.get(TIMESTAMP)).longValue());
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
            converter.accept(TIMESTAMP, storageData.getTimestamp());
            Layer layer = storageData.getLayer();
            converter.accept(LAYER, layer != null ? layer.value() : Layer.UNDEFINED.value());
        }
    }
}
