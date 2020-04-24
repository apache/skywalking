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

package org.apache.skywalking.oap.server.storage.plugin.jaeger;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.ScopeDeclaration;
import org.apache.skywalking.oap.server.core.source.Source;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.JAEGER_SPAN;

@ScopeDeclaration(id = JAEGER_SPAN, name = "JaegerSpan")
public class JaegerSpan extends Source {

    @Override
    public int scope() {
        return DefaultScopeDefine.JAEGER_SPAN;
    }

    @Override
    public String getEntityId() {
        return traceId + spanId;
    }

    @Setter
    @Getter
    private String traceId;
    @Setter
    @Getter
    private String spanId;
    @Setter
    @Getter
    private String serviceId;
    @Setter
    @Getter
    private String serviceInstanceId;
    @Setter
    @Getter
    private String endpointName;
    @Setter
    @Getter
    private String endpointId;
    @Setter
    @Getter
    private long startTime;
    @Setter
    @Getter
    private long endTime;
    @Setter
    @Getter
    private int latency;
    @Setter
    @Getter
    private int isError;
    @Setter
    @Getter
    private byte[] dataBinary;
    @Setter
    @Getter
    private int encode;
}
