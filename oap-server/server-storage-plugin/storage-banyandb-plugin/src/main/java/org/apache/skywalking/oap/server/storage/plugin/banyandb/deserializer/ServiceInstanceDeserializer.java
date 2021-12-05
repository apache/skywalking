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

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.query.enumeration.Language;
import org.apache.skywalking.oap.server.core.query.type.Attribute;
import org.apache.skywalking.oap.server.core.query.type.ServiceInstance;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ServiceInstanceDeserializer extends AbstractBanyanDBDeserializer<ServiceInstance> {
    public ServiceInstanceDeserializer() {
        super(InstanceTraffic.INDEX_NAME,
                ImmutableList.of(InstanceTraffic.SERVICE_ID, InstanceTraffic.LAST_PING_TIME_BUCKET),
                Collections.singletonList("data_binary"));
    }

    @Override
    public ServiceInstance map(RowEntity row) {
        InstanceTraffic instanceTraffic = new InstanceTraffic();
        final List<TagAndValue<?>> data = row.getTagFamilies().get(1);
        Object o = data.get(0).getValue();
        ServiceInstance serviceInstance = new ServiceInstance();
        if (o instanceof ByteString && !((ByteString) o).isEmpty()) {
            try {
                RemoteData remoteData = RemoteData.parseFrom((ByteString) o);
                instanceTraffic.deserialize(remoteData);
                serviceInstance.setName(instanceTraffic.getName());
                serviceInstance.setId(instanceTraffic.getServiceId());

                if (instanceTraffic.getProperties() != null) {
                    for (Map.Entry<String, JsonElement> property : instanceTraffic.getProperties().entrySet()) {
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
            } catch (InvalidProtocolBufferException ex) {
                throw new RuntimeException("fail to parse remote data", ex);
            }
        } else {
            throw new RuntimeException("unable to parse binary data");
        }

        return serviceInstance;
    }
}
