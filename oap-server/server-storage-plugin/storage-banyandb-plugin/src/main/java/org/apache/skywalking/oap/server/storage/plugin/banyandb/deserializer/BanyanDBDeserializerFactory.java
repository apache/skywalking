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
        register(AlarmMessage.class, new AlarmMessageMapper());
        register(BasicTrace.class, new BasicTraceMapper());
        register(BrowserErrorLog.class, new BrowserErrorLogMapper());
        register(DashboardConfiguration.class, new DashboardConfigurationMapper());
        register(Database.class, new DatabaseMapper());
        register(Endpoint.class, new EndpointMapper());
        register(Event.class, new EventMapper());
        register(Log.class, new LogMapper());
        register(NetworkAddressAlias.class, new NetworkAddressAliasMapper());
        register(ProfileTaskLog.class, new ProfileTaskLogMapper());
        register(ProfileTask.class, new ProfileTaskMapper());
        register(ProfileThreadSnapshotRecord.class, new ProfileThreadSnapshotRecordMapper());
        register(SegmentRecord.class, new SegmentRecordMapper());
        register(ServiceInstance.class, new ServiceInstanceMapper());
        register(Service.class, new ServiceMapper());
    }

    private <T> void register(Class<T> clazz, AbstractBanyanDBDeserializer<T> mapper) {
        this.registry.put(clazz, mapper);
    }

    @SuppressWarnings({"unchecked"})
    public <T> AbstractBanyanDBDeserializer<T> findDeserializer(Class<T> clazz) {
        return (AbstractBanyanDBDeserializer<T>) registry.get(clazz);
    }
}
