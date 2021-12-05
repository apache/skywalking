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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.alarm.AlarmRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.log.LogRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.networkalias.NetworkAddressAlias;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceTraffic;
import org.apache.skywalking.oap.server.core.browser.manual.errorlog.BrowserErrorLogRecord;
import org.apache.skywalking.oap.server.core.management.ui.template.UITemplate;
import org.apache.skywalking.oap.server.core.profile.ProfileTaskLogRecord;
import org.apache.skywalking.oap.server.core.profile.ProfileTaskRecord;
import org.apache.skywalking.oap.server.core.profile.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.core.source.Event;
import org.apache.skywalking.oap.server.core.storage.AbstractDAO;
import org.apache.skywalking.oap.server.core.storage.IManagementDAO;
import org.apache.skywalking.oap.server.core.storage.IMetricsDAO;
import org.apache.skywalking.oap.server.core.storage.INoneStreamDAO;
import org.apache.skywalking.oap.server.core.storage.IRecordDAO;
import org.apache.skywalking.oap.server.core.storage.StorageDAO;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.schema.AlarmRecordBuilder;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.schema.BrowserErrorLogRecordBuilder;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.schema.EventBuilder;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.schema.LogRecordBuilder;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.schema.Metadata;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.schema.NetworkAddressAliasBuilder;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.schema.ProfileTaskLogRecordBuilder;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.schema.ProfileTaskRecordBuilder;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.schema.ProfileThreadSnapshotRecordBuilder;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.schema.SegmentRecordBuilder;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.schema.UITemplateBuilder;

import java.util.Map;

@Slf4j
public class BanyanDBStorageDAO extends AbstractDAO<BanyanDBStorageClient> implements StorageDAO {
    public BanyanDBStorageDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public IMetricsDAO newMetricsDao(StorageBuilder storageBuilder) {
        try {
            Class<?> returnType = storageBuilder.getClass().getMethod("storage2Entity", Map.class).getReturnType();
            if (Event.class.equals(returnType)) {
                return new BanyanDBMetricsDAO<>(new EventBuilder());
            } else if (ServiceTraffic.class.equals(returnType)) {
                return new BanyanDBMetricsDAO<>(new Metadata.ServiceTrafficBuilder());
            } else if (InstanceTraffic.class.equals(returnType)) {
                return new BanyanDBMetricsDAO<>(new Metadata.InstanceTrafficBuilder());
            } else if (EndpointTraffic.class.equals(returnType)) {
                return new BanyanDBMetricsDAO<>(new Metadata.EndpointTrafficBuilder());
            } else if (NetworkAddressAlias.class.equals(returnType)) {
                return new BanyanDBMetricsDAO<>(new NetworkAddressAliasBuilder());
            } else {
                throw new IllegalStateException("record type is not supported");
            }
        } catch (NoSuchMethodException noSuchMethodException) {
            log.error("cannot find method storage2Entity", noSuchMethodException);
            throw new RuntimeException("cannot find method storage2Entity");
        }
    }

    @Override
    public IRecordDAO newRecordDao(StorageBuilder storageBuilder) {
        try {
            Class<?> returnType = storageBuilder.getClass().getMethod("storage2Entity", Map.class).getReturnType();
            if (SegmentRecord.class.equals(returnType)) {
                return new BanyanDBRecordDAO<>(new SegmentRecordBuilder());
            } else if (AlarmRecord.class.equals(returnType)) {
                return new BanyanDBRecordDAO<>(new AlarmRecordBuilder());
            } else if (BrowserErrorLogRecord.class.equals(returnType)) {
                return new BanyanDBRecordDAO<>(new BrowserErrorLogRecordBuilder());
            } else if (LogRecord.class.equals(returnType)) {
                return new BanyanDBRecordDAO<>(new LogRecordBuilder());
            } else if (ProfileTaskLogRecord.class.equals(returnType)) {
                return new BanyanDBRecordDAO<>(new ProfileTaskLogRecordBuilder());
            } else if (ProfileThreadSnapshotRecord.class.equals(returnType)) {
                return new BanyanDBRecordDAO<>(new ProfileThreadSnapshotRecordBuilder());
            } else {
                throw new IllegalStateException("record type is not supported");
            }
        } catch (NoSuchMethodException noSuchMethodException) {
            log.error("cannot find method storage2Entity", noSuchMethodException);
            throw new RuntimeException("cannot find method storage2Entity");
        }
    }

    @Override
    public INoneStreamDAO newNoneStreamDao(StorageBuilder storageBuilder) {
        try {
            Class<?> returnType = storageBuilder.getClass().getMethod("storage2Entity", Map.class).getReturnType();
            if (ProfileTaskRecord.class.equals(returnType)) {
                return new BanyanDBNoneStreamDAO<>(getClient(), new ProfileTaskRecordBuilder());
            } else {
                throw new IllegalStateException("record type is not supported");
            }
        } catch (NoSuchMethodException noSuchMethodException) {
            log.error("cannot find method storage2Entity", noSuchMethodException);
            throw new RuntimeException("cannot find method storage2Entity");
        }
    }

    @Override
    public IManagementDAO newManagementDao(StorageBuilder storageBuilder) {
        try {
            Class<?> returnType = storageBuilder.getClass().getMethod("storage2Entity", Map.class).getReturnType();
            if (UITemplate.class.equals(returnType)) {
                return new BanyanDBManagementDAO<>(getClient(), new UITemplateBuilder());
            } else {
                throw new IllegalStateException("record type is not supported");
            }
        } catch (NoSuchMethodException noSuchMethodException) {
            log.error("cannot find method storage2Entity", noSuchMethodException);
            throw new RuntimeException("cannot find method storage2Entity");
        }
    }
}
