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
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.RuntimeContext;
import org.apache.skywalking.apm.agent.core.context.tag.StringTag;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.plugin.thrift.AbstractProcessorInterceptor;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TType;
import org.apache.thrift.transport.TTransport;

/**
 * AsyncServer Input Protocol
 */
public class ServerInProtocolWrapper extends AbstractProtocolWrapper {
    private static final ILog logger = LogManager.getLogger(ServerInProtocolWrapper.class);
    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    private String peer;
    private long startTime = -1;
    private String methodName;
    private boolean hasMetadata = false;

    private String headerKey = null;
    private Map<String, String> header = null;

    public ServerInProtocolWrapper(final TProtocol protocol) {
        super(protocol);
        TTransport transport = protocol.getTransport();
        if (transport instanceof EnhancedInstance) {
            peer = (String) ((EnhancedInstance) transport).getSkyWalkingDynamicField();
        }
    }

    @Override
    public TMessage readMessageBegin() throws TException {
        TMessage message = super.readMessageBegin();
        startTime = System.currentTimeMillis();
        methodName = message.name;
        return message;
    }

    @Override
    public TField readFieldBegin() throws TException {
        TField field = super.readFieldBegin();
        if (field.id == SW_MAGIC_FIELD_ID && field.type == TType.MAP) {
            hasMetadata = true;
            if (header == null) {
                header = Maps.newHashMap();
            }
        }
        return field;
    }

    @Override
    public ByteBuffer readBinary() throws TException {
        if (hasMetadata) {
            try {
                if (headerKey == null) {
                    headerKey = super.readString();
                } else {
                    header.put(headerKey, readString());
                    headerKey = null;
                }
            } catch (Throwable throwable) {
                logger.error("Thrift Header parses failed.", throwable);
            }
            return EMPTY_BUFFER;
        }
        return super.readBinary();
    }

    @Override
    public void readFieldEnd() throws TException {
        if (hasMetadata) {
            hasMetadata = false;
        }
        super.readFieldEnd();
    }

    @Override
    public void readMessageEnd() throws TException {
        try {
            super.readMessageEnd();
        } finally {
            final RuntimeContext context = ContextManager.getRuntimeContext();
            final String operationName = context.get("prefix") + methodName;
            final ContextCarrier carrier = new ContextCarrier();

            if (Objects.nonNull(header)) {
                CarrierItem items = carrier.items();
                while (items.hasNext()) {
                    items = items.next();
                    items.setHeadValue(header.get(items.getHeadKey()));
                }
            }
            String remote = peer;
            if (StringUtil.isEmpty(peer)) {
                remote = context.get("peer", String.class);
            }

            AbstractSpan span = ContextManager.createEntrySpan(operationName, carrier);
            span.start(startTime);
            span.setPeer(remote);
            SpanLayer.asRPCFramework(span);

            AbstractProcessorInterceptor processor = context.get("processor", AbstractProcessorInterceptor.class);
            span.tag(new StringTag("params"), processor.getArgumentNames(methodName));

            header = null;
            headerKey = null;
            hasMetadata = false;
            methodName = null;
        }
    }

}
