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

package org.apache.skywalking.apm.agent.core.context.trace;

import org.apache.skywalking.apm.agent.core.context.TracingContext;
import org.apache.skywalking.apm.agent.core.dictionary.DictionaryManager;
import org.apache.skywalking.apm.agent.core.dictionary.DictionaryUtil;
import org.apache.skywalking.apm.agent.core.dictionary.PossibleFound;
import org.apache.skywalking.apm.network.language.agent.v2.SpanObjectV2;

/**
 * The <code>StackBasedTracingSpan</code> represents a span with an inside stack construction.
 *
 * This kind of span can start and finish multi times in a stack-like invoke line.
 *
 * @author wusheng
 */
public abstract class StackBasedTracingSpan extends AbstractTracingSpan {
    protected int stackDepth;
    protected String peer;
    protected int peerId;

    protected StackBasedTracingSpan(int spanId, int parentSpanId, String operationName, TracingContext owner) {
        super(spanId, parentSpanId, operationName, owner);
        this.stackDepth = 0;
        this.peer = null;
        this.peerId = DictionaryUtil.nullValue();
    }

    protected StackBasedTracingSpan(int spanId, int parentSpanId, int operationId, TracingContext owner) {
        super(spanId, parentSpanId, operationId, owner);
        this.stackDepth = 0;
        this.peer = null;
        this.peerId = DictionaryUtil.nullValue();
    }

    public StackBasedTracingSpan(int spanId, int parentSpanId, int operationId, int peerId, TracingContext owner) {
        super(spanId, parentSpanId, operationId, owner);
        this.peer = null;
        this.peerId = peerId;
    }

    public StackBasedTracingSpan(int spanId, int parentSpanId, int operationId, String peer, TracingContext owner) {
        super(spanId, parentSpanId, operationId, owner);
        this.peer = peer;
        this.peerId = DictionaryUtil.nullValue();
    }

    protected StackBasedTracingSpan(int spanId, int parentSpanId, String operationName, String peer, TracingContext owner) {
        super(spanId, parentSpanId, operationName, owner);
        this.peer = peer;
        this.peerId = DictionaryUtil.nullValue();
    }

    protected StackBasedTracingSpan(int spanId, int parentSpanId, String operationName, int peerId, TracingContext owner) {
        super(spanId, parentSpanId, operationName, owner);
        this.peer = null;
        this.peerId = peerId;
    }

    @Override
    public SpanObjectV2.Builder transform() {
        SpanObjectV2.Builder spanBuilder = super.transform();
        if (peerId != DictionaryUtil.nullValue()) {
            spanBuilder.setPeerId(peerId);
        } else {
            if (peer != null) {
                spanBuilder.setPeer(peer);
            }
        }
        return spanBuilder;
    }

    @Override
    public boolean finish(TraceSegment owner) {
        if (--stackDepth == 0) {
            /**
             * Since 6.6.0, only entry span requires the op name register, which is endpoint.
             */
            if (this.isEntry()) {
                if (this.operationId == DictionaryUtil.nullValue()) {
                    this.operationId = (Integer)DictionaryManager.findEndpointSection()
                        .findOrPrepare4Register(owner.getServiceId(), operationName)
                        .doInCondition(
                            new PossibleFound.FoundAndObtain() {
                                @Override public Object doProcess(int value) {
                                    return value;
                                }
                            },
                            new PossibleFound.NotFoundAndObtain() {
                                @Override public Object doProcess() {
                                    return DictionaryUtil.nullValue();
                                }
                            }
                        );
                }
            }
            return super.finish(owner);
        } else {
            return false;
        }
    }

    @Override public AbstractSpan setPeer(final String remotePeer) {
        DictionaryManager.findNetworkAddressSection().find(remotePeer).doInCondition(
            new PossibleFound.Found() {
                @Override
                public void doProcess(int remotePeerId) {
                    peerId = remotePeerId;
                }
            }, new PossibleFound.NotFound() {
                @Override
                public void doProcess() {
                    peer = remotePeer;
                }
            }
        );
        return this;
    }
}
