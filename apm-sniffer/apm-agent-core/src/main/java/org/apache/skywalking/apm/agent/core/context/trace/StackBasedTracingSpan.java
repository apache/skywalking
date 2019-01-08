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

import org.apache.skywalking.apm.agent.core.dictionary.DictionaryManager;
import org.apache.skywalking.apm.agent.core.dictionary.DictionaryUtil;
import org.apache.skywalking.apm.agent.core.dictionary.PossibleFound;

/**
 * The <code>StackBasedTracingSpan</code> represents a span with an inside stack construction.
 *
 * This kind of span can start and finish multi times in a stack-like invoke line.
 *
 * @author wusheng
 */
public abstract class StackBasedTracingSpan extends AbstractTracingSpan {
    protected int stackDepth;

    protected StackBasedTracingSpan(int spanId, int parentSpanId, String operationName) {
        super(spanId, parentSpanId, operationName);
        this.stackDepth = 0;
    }

    protected StackBasedTracingSpan(int spanId, int parentSpanId, int operationId) {
        super(spanId, parentSpanId, operationId);
        this.stackDepth = 0;
    }

    @Override
    public boolean finish(TraceSegment owner) {
        if (--stackDepth == 0) {
            if (this.operationId == DictionaryUtil.nullValue()) {
                this.operationId = (Integer)DictionaryManager.findEndpointSection()
                    .findOrPrepare4Register(owner.getServiceId(), operationName, this.isEntry(), this.isExit())
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
            return super.finish(owner);
        } else {
            return false;
        }
    }
}
