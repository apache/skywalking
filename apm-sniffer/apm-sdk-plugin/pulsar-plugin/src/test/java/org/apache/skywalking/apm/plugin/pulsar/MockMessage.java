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

package org.apache.skywalking.apm.plugin.pulsar;

import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.impl.MessageImpl;
import org.apache.pulsar.common.api.proto.PulsarApi;
import org.apache.pulsar.shade.io.netty.buffer.ByteBuf;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MockMessage extends MessageImpl implements EnhancedInstance {

    private PulsarApi.MessageMetadata.Builder msgMetadataBuilder = PulsarApi.MessageMetadata.newBuilder();

    private transient Map<String, String> properties;

    private Object enhancedSkyWalkingField;

    public MockMessage() {
        this(null, "1:1", new HashMap(), null, null);
    }

    public MockMessage(String topic, String msgId, Map properties, ByteBuf payload, Schema schema) {
        super(topic, msgId, properties, payload, schema);
    }

    @Override
    public PulsarApi.MessageMetadata.Builder getMessageBuilder() {
        return msgMetadataBuilder;
    }

    public synchronized Map<String, String> getProperties() {
        if (this.properties == null) {
            if (this.msgMetadataBuilder.getPropertiesCount() > 0) {
                Map<String, String> internalProperties = new HashMap<String, String>();
                for (int i = 0; i < this.msgMetadataBuilder.getPropertiesCount(); i++) {
                    PulsarApi.KeyValue kv = this.msgMetadataBuilder.getProperties(i);
                    internalProperties.put(kv.getKey(), kv.getValue());
                }
                this.properties = Collections.unmodifiableMap(internalProperties);
            } else {
                this.properties = Collections.emptyMap();
            }
        }
        return this.properties;
    }

    @Override
    public String getProperty(String name) {
        return this.getProperties().get(name);
    }

    @Override
    public Object getSkyWalkingDynamicField() {
        return enhancedSkyWalkingField;
    }

    @Override
    public void setSkyWalkingDynamicField(Object value) {
        this.enhancedSkyWalkingField = value;
    }
}
