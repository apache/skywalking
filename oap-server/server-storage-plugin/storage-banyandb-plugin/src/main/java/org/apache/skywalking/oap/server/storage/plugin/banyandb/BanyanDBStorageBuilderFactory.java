package org.apache.skywalking.oap.server.storage.plugin.banyandb;

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
import org.apache.skywalking.oap.server.core.storage.StorageBuilderFactory;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
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

public class BanyanDBStorageBuilderFactory implements StorageBuilderFactory {
    @Override
    public BuilderTemplateDefinition builderTemplate() {
        return new BuilderTemplateDefinition(StorageHashMapBuilder.class.getName(), "metrics-builder");
    }

    @Override
    public Class<? extends StorageBuilder> builderOf(Class<? extends StorageData> dataType, Class<? extends StorageBuilder> defaultBuilder) {
        if (SegmentRecord.class.equals(dataType)) {
            return SegmentRecordBuilder.class;
        } else if (AlarmRecord.class.equals(dataType)) {
            return AlarmRecordBuilder.class;
        } else if (BrowserErrorLogRecord.class.equals(dataType)) {
            return BrowserErrorLogRecordBuilder.class;
        } else if (LogRecord.class.equals(dataType)) {
            return LogRecordBuilder.class;
        } else if (ProfileTaskLogRecord.class.equals(dataType)) {
            return ProfileTaskLogRecordBuilder.class;
        } else if (ProfileThreadSnapshotRecord.class.equals(dataType)) {
            return ProfileThreadSnapshotRecordBuilder.class;
        } else if (ProfileTaskRecord.class.equals(dataType)) {
            return ProfileTaskRecordBuilder.class;
        } else if (UITemplate.class.equals(dataType)) {
            return UITemplateBuilder.class;
        } else if (Event.class.equals(dataType)) {
            return EventBuilder.class;
        } else if (ServiceTraffic.class.equals(dataType)) {
            return Metadata.ServiceTrafficBuilder.class;
        } else if (InstanceTraffic.class.equals(dataType)) {
            return Metadata.InstanceTrafficBuilder.class;
        } else if (EndpointTraffic.class.equals(dataType)) {
            return Metadata.EndpointTrafficBuilder.class;
        } else if (NetworkAddressAlias.class.equals(dataType)) {
            return NetworkAddressAliasBuilder.class;
        }

        throw new UnsupportedOperationException("unsupported storage type");
    }
}
