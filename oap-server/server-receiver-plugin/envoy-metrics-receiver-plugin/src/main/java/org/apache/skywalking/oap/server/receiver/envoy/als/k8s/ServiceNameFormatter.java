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

package org.apache.skywalking.oap.server.receiver.envoy.als.k8s;

import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.StringUtils;

public class ServiceNameFormatter {

    private final List<String> properties;

    private final StringBuffer serviceNamePattern;

    public ServiceNameFormatter(String rule) {
        rule = StringUtils.defaultIfBlank(rule, "${pod.metadata.labels.(service.istio.io/canonical-name)}");

        this.properties = new ArrayList<>();
        this.serviceNamePattern = new StringBuffer();

        final Pattern variablePattern = Pattern.compile("(\\$\\{(?<property>.+?)})");
        final Matcher matcher = variablePattern.matcher(rule);

        while (matcher.find()) {
            properties.add(matcher.group("property"));
            matcher.appendReplacement(serviceNamePattern, "%s");
        }
    }

    public String format(final Map<String, Object> context) throws Exception {
        final Object[] values = new Object[properties.size()];

        for (int i = 0; i < properties.size(); i++) {
            final Object value = PropertyUtils.getProperty(context, properties.get(i));
            values[i] = value;
        }

        return Strings.lenientFormat(serviceNamePattern.toString(), values);
    }
}
