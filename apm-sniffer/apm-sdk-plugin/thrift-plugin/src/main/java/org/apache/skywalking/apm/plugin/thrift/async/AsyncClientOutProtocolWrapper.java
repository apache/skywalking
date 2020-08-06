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

package org.apache.skywalking.apm.plugin.thrift.async;

import com.google.common.collect.Maps;
import java.util.Map;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.plugin.thrift.wrapper.AbstractProtocolWrapper;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TProtocol;

/**
 * AsyncServer Output Protocol
 */
public class AsyncClientOutProtocolWrapper extends AbstractProtocolWrapper {
    private static final ILog logger = LogManager.getLogger(AsyncClientOutProtocolWrapper.class);
    private boolean injected = true;

    public AsyncClientOutProtocolWrapper(final TProtocol protocol) {
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
            ContextCarrier carrier = new ContextCarrier();
            ContextManager.inject(carrier);
            CarrierItem items = carrier.items();

            Map<String, String> header = Maps.newHashMap();
            while (items.hasNext()) {
                items = items.next();
                header.put(items.getHeadKey(), items.getHeadValue());
            }
            if (!header.isEmpty()) {
                try {
                    super.writeHeader(header);
                    injected = true;
                } catch (Throwable throwable) {
                    logger.error("Propagating CarrierItems failed.", throwable);
                }
            }
        }
        super.writeFieldStop();
    }

}
