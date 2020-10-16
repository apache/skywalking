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

package org.apache.skywalking.apm.plugin.thrift.wrapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TMap;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TType;

/**
 * Wrapping client output protocol for injecting and propagating the trace header. This is also safe even if the server
 * doesn't deal with it.
 */
public class ClientOutProtocolWrapper extends AbstractProtocolWrapper {
    private static final ILog LOGGER = LogManager.getLogger(ClientOutProtocolWrapper.class);
    private boolean injected = true;

    public ClientOutProtocolWrapper(final TProtocol protocol) {
        super(protocol);
    }

    @Override
    public final void writeMessageBegin(final TMessage message) throws TException {
        injected = false;
        super.writeMessageBegin(message);
    }

    @Override
    public final void writeFieldStop() throws TException {
        if (!injected && ContextManager.isActive()) {
            try {
                final ContextCarrier carrier = new ContextCarrier();
                ContextManager.inject(carrier);
                CarrierItem items = carrier.items();

                final Map<String, String> header = new HashMap<>(3);
                while (items.hasNext()) {
                    items = items.next();
                    header.put(items.getHeadKey(), items.getHeadValue());
                }
                writeHeader(header);
            } catch (Throwable throwable) {
                LOGGER.error("Failed to propagating CarrierItems.", throwable);
            } finally {
                injected = true;
            }
        }
        super.writeFieldStop();
    }

    private void writeHeader(Map<String, String> header) throws TException {
        super.writeFieldBegin(new TField(SW_MAGIC_FIELD, TType.MAP, SW_MAGIC_FIELD_ID));
        super.writeMapBegin(new TMap(TType.STRING, TType.STRING, header.size()));

        final Set<Map.Entry<String, String>> entries = header.entrySet();
        for (Map.Entry<String, String> entry : entries) {
            super.writeString(entry.getKey());
            super.writeString(entry.getValue());
        }

        super.writeMapEnd();
        super.writeFieldEnd();
    }

}
