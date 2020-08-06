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

import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Objects;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TProtocol;

/**
 * AsyncServer Output Protocol
 */
public class ClientOutProtocolWrapper extends AbstractProtocolWrapper {
    private static final ILog logger = LogManager.getLogger(ClientOutProtocolWrapper.class);
    private boolean injected = true;
    private Map<String, String> header = null;

    public ClientOutProtocolWrapper(final TProtocol protocol) {
        super(protocol);
    }

    @Override
    public void writeFieldStop() throws TException {
        // it will be called more than one times when the method has several arguments.
        if (!injected && Objects.nonNull(header)) {
            try {
                super.writeHeader(header);

                injected = true;
                header = null;
            } catch (TException throwable) {
                logger.error("Propagating CarrierItems failed", throwable);
            }
        }
        super.writeFieldStop();
    }

    @Override
    public void writeMessageEnd() throws TException {
        injected = true;
        header = null;
        super.writeMessageEnd();
    }

    public void inject(ContextCarrier carrier) {
        if (header == null) {
            injected = false;
            header = Maps.newHashMap();
        }
        CarrierItem items = carrier.items();
        while (items.hasNext()) {
            items = items.next();
            header.put(items.getHeadKey(), items.getHeadValue());
        }
    }

    public void setHeader(String key, String value) {
        if (header == null) {
            injected = false;
            header = Maps.newHashMap();
        }
        header.put(key, value);
    }
}
