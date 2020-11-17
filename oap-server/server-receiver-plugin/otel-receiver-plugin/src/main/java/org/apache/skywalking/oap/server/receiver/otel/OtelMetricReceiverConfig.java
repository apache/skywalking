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

package org.apache.skywalking.oap.server.receiver.otel;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

@Slf4j
public class OtelMetricReceiverConfig extends ModuleConfig {

    private String enabledHandlers;

    private String enabledOcRules;

    public List<String> getEnabledHandlers() {
        return split(enabledHandlers);
    }

    List<String> getEnabledRulesFrom(String handler) {
        Field f;
        try {
            f = this.getClass().getDeclaredField(String.format("enabled%sRules", StringUtils.capitalize(handler)));
        } catch (NoSuchFieldException e) {
            if (log.isDebugEnabled()) {
                log.debug("failed to get disabled rule field of {}", handler, e);
            }
            return Collections.emptyList();
        }
        f.setAccessible(true);
        try {
            return  split(f.get(this));
        } catch (IllegalAccessException e) {
            log.warn("failed to access disabled rule list of {}", handler, e);
            return Collections.emptyList();
        }
    }

    private List<String> split(Object str) {
        return Arrays.stream(Optional.ofNullable(str).orElse("").toString()
            .split(","))
            .map(String::trim)
            .filter(StringUtil::isNotEmpty)
            .collect(Collectors.toList());
    }

}
