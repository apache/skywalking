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
import java.util.Objects;

import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.StringTag;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TMap;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TType;

/**
 * Wrapping server input protocol for reading and parsing the trace header from the original protocol. It is a special
 * field which will be skipped without one deal with.
 */
public class ServerInProtocolWrapper extends AbstractProtocolWrapper {
    private static final ILog LOGGER = LogManager.getLogger(ServerInProtocolWrapper.class);
    private static final StringTag TAG_ARGS = new StringTag("args");
    private AbstractContext context;
    private static final String HAVE_CREATED_SPAN = "HAVE_CREATED_SPAN";

    public ServerInProtocolWrapper(final TProtocol protocol) {
        super(protocol);
    }

    public void initial(AbstractContext context) {
        this.context = context;
        ContextManager.getRuntimeContext().put(HAVE_CREATED_SPAN, false);
    }

    @Override
    public TField readFieldBegin() throws TException {
        final TField field = super.readFieldBegin();
        if (field.id == SW_MAGIC_FIELD_ID && field.type == TType.MAP) {
            try {
                TMap tMap = super.readMapBegin();
                Map<String, String> header = new HashMap<>(tMap.size);

                for (int i = 0; i < tMap.size; i++) {
                    header.put(readString(), readString());
                }

                AbstractSpan span = ContextManager.createEntrySpan(
                    context.getOperatorName(), createContextCarrier(header));
                span.start(context.startTime);
                span.tag(TAG_ARGS, context.getArguments());
                span.setComponent(ComponentsDefine.THRIFT_SERVER);
                SpanLayer.asRPCFramework(span);
                ContextManager.getRuntimeContext().put(HAVE_CREATED_SPAN, true);
            } catch (Throwable throwable) {
                LOGGER.error("Failed to resolve header or create EntrySpan.", throwable);
            } finally {
                context = null;
                super.readMapEnd();
                super.readFieldEnd();
            }
            return readFieldBegin();
        }

        return field;
    }

    @Override
    public void readMessageEnd() throws TException {
        super.readMessageEnd();
        Boolean haveCreatedSpan =
                (Boolean) ContextManager.getRuntimeContext().get(HAVE_CREATED_SPAN);
        if (haveCreatedSpan != null && !haveCreatedSpan) {
            try {
                AbstractSpan span = ContextManager.createEntrySpan(
                        context.getOperatorName(), createContextCarrier(null));
                span.start(context.startTime);
                span.tag(TAG_ARGS, context.getArguments());
                span.setComponent(ComponentsDefine.THRIFT_SERVER);
                SpanLayer.asRPCFramework(span);
            } catch (Throwable throwable) {
                LOGGER.error("Failed to create EntrySpan.", throwable);
            } finally {
                context = null;
            }
        }
    }

    private ContextCarrier createContextCarrier(Map<String, String> header) {
        ContextCarrier carrier = new ContextCarrier();
        if (Objects.nonNull(header)) {
            CarrierItem items = carrier.items();
            while (items.hasNext()) {
                items = items.next();
                items.setHeadValue(header.get(items.getHeadKey()));
            }
        }
        return carrier;
    }

    @Override
    public TMessage readMessageBegin() throws TException {
        final TMessage message = super.readMessageBegin();
        if (Objects.nonNull(message)) {
            context.setup(message.name);
        }
        return message;
    }

}
