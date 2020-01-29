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

import org.apache.avro.ipc.RPCContext;
import org.apache.avro.ipc.RPCPlugin;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;

import java.nio.ByteBuffer;

/**
 * A SkyWalking tracing plugin for Avro Client.
 * Inject CarrierItems into RPC's metadata from cross-process propagation.
 */
public class SWClientRPCPlugin extends RPCPlugin {

    @Override
    public void clientSendRequest(RPCContext context) {
        ContextCarrier carrier = new ContextCarrier();
        ContextManager.inject(carrier);

        CarrierItem items = carrier.items();
        while (items.hasNext()) {
            items = items.next();
            context.requestCallMeta().put(items.getHeadKey(), ByteBuffer.wrap(items.getHeadValue().getBytes()));
        }
    }

}
