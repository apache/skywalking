package org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.query.enumeration.Language;
import org.apache.skywalking.oap.server.core.query.type.Attribute;
import org.apache.skywalking.oap.server.core.query.type.ServiceInstance;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.library.util.StringUtil;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ServiceInstanceMapper extends AbstractBanyanDBDeserializer<ServiceInstance> {
    private static final Gson GSON = new Gson();

    public ServiceInstanceMapper() {
        super(InstanceTraffic.INDEX_NAME,
                ImmutableList.of(InstanceTraffic.SERVICE_ID, InstanceTraffic.LAST_PING_TIME_BUCKET),
                Collections.singletonList("data_binary"));
    }

    @Override
    public ServiceInstance map(RowEntity row) {
        ServiceInstance serviceInstance = new ServiceInstance();
        final List<TagAndValue<?>> searchable = row.getTagFamilies().get(0);
        serviceInstance.setId((String) searchable.get(0).getValue());
        serviceInstance.setInstanceUUID((String) searchable.get(0).getValue());
        final List<TagAndValue<?>> data = row.getTagFamilies().get(1);
        Object o = data.get(0).getValue();
        if (o instanceof ByteString && !((ByteString) o).isEmpty()) {
            try {
                RemoteData remoteData = RemoteData.parseFrom((ByteString) o);
                serviceInstance.setName(remoteData.getDataStrings(1));
                final String propString = remoteData.getDataStrings(2);
                if (StringUtil.isNotEmpty(propString)) {
                    JsonObject properties = GSON.fromJson(propString, JsonObject.class);
                    if (properties != null) {
                        for (Map.Entry<String, JsonElement> property : properties.entrySet()) {
                            String key = property.getKey();
                            String value = property.getValue().getAsString();
                            if (key.equals(InstanceTraffic.PropertyUtil.LANGUAGE)) {
                                serviceInstance.setLanguage(Language.value(value));
                            } else {
                                serviceInstance.getAttributes().add(new Attribute(key, value));
                            }
                        }
                    } else {
                        serviceInstance.setLanguage(Language.UNKNOWN);
                    }
                } else {
                    serviceInstance.setLanguage(Language.UNKNOWN);
                }
            } catch (InvalidProtocolBufferException ex) {
                throw new RuntimeException("fail to parse remote data", ex);
            }
        } else {
            serviceInstance.setLanguage(Language.UNKNOWN);
        }
        return serviceInstance;
    }
}
