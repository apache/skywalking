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
 */

package org.apache.skywalking.oap.trace.analyzer.v2.compiler;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.core.source.TALOutputBuilder;
import org.apache.skywalking.oap.server.core.trace.SpanMetadata;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * The setters the shipped slow-statement rule writes to, so the attribution test can compile it without pulling
 * in {@code agent-analyzer} for the real builder.
 */
@Getter
@Setter
public class AttributionSlowStatement implements TALOutputBuilder {
    private String id;
    private String statement;
    private long latency;

    @Override
    public String name() {
        return "SlowSQL";
    }

    @Override
    public void init(final SpanMetadata meta, final Object input, final ModuleManager moduleManager) {
    }

    @Override
    public void complete(final SourceReceiver sourceReceiver) {
    }

    @Override
    public String outputToJson() {
        return "{}";
    }
}
