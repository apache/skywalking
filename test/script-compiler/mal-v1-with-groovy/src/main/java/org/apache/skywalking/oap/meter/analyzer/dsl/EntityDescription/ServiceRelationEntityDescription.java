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
import com.google.common.collect.ImmutableList;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.meter.ScopeType;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.library.util.StringUtil;

@Getter
@RequiredArgsConstructor
@ToString
public class ServiceRelationEntityDescription implements EntityDescription {
    private final ScopeType scopeType = ScopeType.SERVICE_RELATION;
    private final List<String> sourceServiceKeys;
    private final List<String> destServiceKeys;
    private final DetectPoint detectPoint;
    private final Layer layer;
    private final String delimiter;
    private final String componentIdKey;

    @Override
    public List<String> getLabelKeys() {
        final ImmutableList.Builder<String> builder = ImmutableList.<String>builder()
            .addAll(this.sourceServiceKeys)
            .addAll(this.destServiceKeys);
        if (StringUtil.isNotEmpty(componentIdKey)) {
            builder.add(componentIdKey);
        }
        return builder.build();
    }
}
