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
package org.apache.skywalking.apm.plugin.avro;

import java.nio.ByteBuffer;
import java.util.Map;
import org.apache.avro.ipc.RPCContext;
import org.apache.avro.ipc.RPCPlugin;
import org.apache.avro.util.Utf8;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

/**
 * A SkyWalking tracing plugin for Avro Server.
 * Extract the CarrierItems from RPC's metadata and inject them into ContextCarrier.
 */
public class SWServerRPCPlugin extends RPCPlugin {
    private final String prefix;

    public SWServerRPCPlugin(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public void serverReceiveRequest(RPCContext context) {
        Map meta = context.requestCallMeta();

        ContextCarrier carrier = new ContextCarrier();
        CarrierItem items = carrier.items();
        while (items.hasNext()) {
            items = items.next();
            ByteBuffer buffer = (ByteBuffer)meta.get(new Utf8(items.getHeadKey()));
            items.setHeadValue(new String(buffer.array()));
        }

        String operationName = prefix + context.getMessage().getName();
        AbstractSpan span = ContextManager.createEntrySpan(operationName, carrier);
        SpanLayer.asRPCFramework(span);
        span.setComponent(ComponentsDefine.AVRO_SERVER);
    }

}
