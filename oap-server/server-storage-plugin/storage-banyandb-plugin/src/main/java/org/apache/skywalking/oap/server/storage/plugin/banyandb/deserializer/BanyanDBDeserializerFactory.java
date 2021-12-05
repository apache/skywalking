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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer;

import org.apache.skywalking.oap.server.core.analysis.manual.networkalias.NetworkAddressAlias;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.profile.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.core.query.type.AlarmMessage;
import org.apache.skywalking.oap.server.core.query.type.BasicTrace;
import org.apache.skywalking.oap.server.core.query.type.BrowserErrorLog;
import org.apache.skywalking.oap.server.core.query.type.DashboardConfiguration;
import org.apache.skywalking.oap.server.core.query.type.Database;
import org.apache.skywalking.oap.server.core.query.type.Endpoint;
import org.apache.skywalking.oap.server.core.query.type.Log;
import org.apache.skywalking.oap.server.core.query.type.ProfileTask;
import org.apache.skywalking.oap.server.core.query.type.ProfileTaskLog;
import org.apache.skywalking.oap.server.core.query.type.Service;
import org.apache.skywalking.oap.server.core.query.type.ServiceInstance;
import org.apache.skywalking.oap.server.core.query.type.event.Event;

import java.util.HashMap;
import java.util.Map;

public enum BanyanDBDeserializerFactory {
    INSTANCE;

    private final Map<Class<?>, AbstractBanyanDBDeserializer<?>> registry;

    BanyanDBDeserializerFactory() {
        registry = new HashMap<>(10);
        register(AlarmMessage.class, new AlarmMessageDeserializer());
        register(BasicTrace.class, new BasicTraceDeserializer());
        register(BrowserErrorLog.class, new BrowserErrorLogDeserializer());
        register(DashboardConfiguration.class, new DashboardConfigurationDeserializer());
        register(Database.class, new DatabaseDeserializer());
        register(Endpoint.class, new EndpointDeserializer());
        register(Event.class, new EventDeserializer());
        register(Log.class, new LogDeserializer());
        register(NetworkAddressAlias.class, new NetworkAddressAliasDeserializer());
        register(ProfileTaskLog.class, new ProfileTaskLogDeserializer());
        register(ProfileTask.class, new ProfileTaskDeserializer());
        register(ProfileThreadSnapshotRecord.class, new ProfileThreadSnapshotRecordDeserializer());
        register(SegmentRecord.class, new SegmentRecordDeserializer());
        register(ServiceInstance.class, new ServiceInstanceDeserializer());
        register(Service.class, new ServiceDeserializer());
    }

    private <T> void register(Class<T> clazz, AbstractBanyanDBDeserializer<T> mapper) {
        this.registry.put(clazz, mapper);
    }

    @SuppressWarnings({"unchecked"})
    public <T> AbstractBanyanDBDeserializer<T> findDeserializer(Class<T> clazz) {
        return (AbstractBanyanDBDeserializer<T>) registry.get(clazz);
    }
}
