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

package org.apache.skywalking.oap.server.receiver.otel.otlp;

import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.library.util.StringUtil;

/**
 * Names an OTLP service in OAP's group convention, {@code group::name}, from a resource attribute that
 * {@code receiver-otel.otlpServiceGroupKeys} lists: the service of the trace analysis, of the stored span, which
 * TraceQL searches by it, and of the logs. The resource attributes, {@code service.name} among them, stay as sent,
 * and the trace analyzer matches the hosts callers dial against the short name.
 */
final class OTLPServiceGroup {
    private OTLPServiceGroup() {
    }

    /**
     * @param serviceName  the service name the resource carries
     * @param resourceTags the resource attributes as strings
     * @param keys         the attribute keys in order of preference; empty groups nothing
     * @return {@code value::serviceName} for the first key the resource carries a value for; the name unchanged
     * when no key has one, when the name is grouped already, or when the value holds {@code ::} itself, which
     * would move the split
     */
    static String apply(final String serviceName, final Map<String, String> resourceTags, final List<String> keys) {
        if (keys.isEmpty() || StringUtil.isEmpty(serviceName) || serviceName.contains(Const.DOUBLE_COLONS_SPLIT)) {
            return serviceName;
        }
        for (final String key : keys) {
            final String group = resourceTags.get(key);
            if (StringUtil.isNotEmpty(group) && !group.trim().isEmpty()) {
                return group.contains(Const.DOUBLE_COLONS_SPLIT)
                    ? serviceName : group.trim() + Const.DOUBLE_COLONS_SPLIT + serviceName;
            }
        }
        return serviceName;
    }
}
