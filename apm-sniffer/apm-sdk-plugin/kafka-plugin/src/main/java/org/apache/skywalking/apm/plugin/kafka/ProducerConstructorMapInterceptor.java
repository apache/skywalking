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

package org.apache.skywalking.apm.plugin.kafka;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.util.StringUtil;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class ProducerConstructorMapInterceptor implements InstanceConstructorInterceptor {
    private static final Pattern COMMA_WITH_WHITESPACE = Pattern.compile("\\s*,\\s*");

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        Map<String, Object> config = (Map<String, Object>) allArguments[0];
        // prevent errors caused by secondary interception in kafkaTemplate
        if (objInst.getSkyWalkingDynamicField() == null) {
            Object bootstrapServers = config.get("bootstrap.servers");
            if (bootstrapServers instanceof List) {
                objInst.setSkyWalkingDynamicField(String.join(";", (List<String>) bootstrapServers));
            } else {
                objInst.setSkyWalkingDynamicField(StringUtil.join(';', COMMA_WITH_WHITESPACE.split((String) bootstrapServers, -1)));
            }
        }
    }
}