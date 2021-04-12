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

package org.apache.skywalking.oap.meter.analyzer.dsl.EntityDescription;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.apache.skywalking.oap.server.core.analysis.meter.ScopeType;

@Getter
@RequiredArgsConstructor
@ToString
public class EndpointEntityDescription implements EntityDescription {
    private final ScopeType scopeType = ScopeType.ENDPOINT;
    private final List<String> serviceKeys;
    private final List<String> endpointKeys;

    @Override
    public List<String> getLabelKeys() {
        return Stream.concat(this.serviceKeys.stream(), this.endpointKeys.stream()).collect(Collectors.toList());
    }

    @Override
    public List<String> getInstanceKeys() {
        throw new UnsupportedOperationException("Unsupported Operation of getInstanceKeys() " + this.toString());
    }
}
