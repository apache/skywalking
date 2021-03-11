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

package org.apache.skywalking.oap.server.receiver.envoy.als.mx;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.apache.skywalking.oap.server.receiver.envoy.als.ServiceMetaInfo;
import org.yaml.snakeyaml.Yaml;

@Slf4j
public enum FieldsHelper {
    SINGLETON;

    private boolean initialized = false;

    /**
     * The mappings from the field name of {@link ServiceMetaInfo} to the field name of {@code flatbuffers}.
     */
    private Map<String, ServiceNameFormat> fieldNameMapping;

    /**
     * The mappings from the field name of {@link ServiceMetaInfo} to its {@code setter}.
     */
    private Map<String, Method> fieldSetterMapping;

    public void init(final String file,
                     final Class<? extends ServiceMetaInfo> serviceInfoClass) throws Exception {
        init(ResourceUtils.readToStream(file), serviceInfoClass);
    }

    @SuppressWarnings("unchecked")
    public void init(final InputStream inputStream,
                     final Class<? extends ServiceMetaInfo> serviceInfoClass) throws ModuleStartException {
        if (initialized) {
            return;
        }

        final Yaml yaml = new Yaml();
        final Map<String, String> config = (Map<String, String>) yaml.load(inputStream);

        fieldNameMapping = new HashMap<>(config.size());
        fieldSetterMapping = new HashMap<>(config.size());

        for (final Map.Entry<String, String> entry : config.entrySet()) {
            final String serviceMetaInfoFieldName = entry.getKey();
            final String flatBuffersFieldName = entry.getValue();

            final Pattern p = Pattern.compile("(\\$\\{(?<property>.+?)})");
            final Matcher m = p.matcher(flatBuffersFieldName);
            final List<List<String>> flatBuffersFieldNames = new ArrayList<>(m.groupCount());
            final StringBuffer serviceNamePattern = new StringBuffer();
            while (m.find()) {
                final String property = m.group("property");
                flatBuffersFieldNames.add(Splitter.on('.').omitEmptyStrings().splitToList(property));
                m.appendReplacement(serviceNamePattern, "%s");
            }

            fieldNameMapping.put(
                serviceMetaInfoFieldName,
                new ServiceNameFormat(serviceNamePattern.toString(), flatBuffersFieldNames)
            );

            try {
                final Method setterMethod = serviceInfoClass.getMethod("set" + StringUtils.capitalize(serviceMetaInfoFieldName), String.class);
                setterMethod.setAccessible(true);
                fieldSetterMapping.put(serviceMetaInfoFieldName, setterMethod);
            } catch (final NoSuchMethodException e) {
                throw new ModuleStartException("Initialize method error", e);
            }
        }
        initialized = true;
    }

    /**
     * Inflates the {@code serviceMetaInfo} with the given {@link Struct struct}.
     *
     * @param metadata        the {@link Struct} metadata from where to retrieve and inflate the {@code serviceMetaInfo}.
     * @param serviceMetaInfo the {@code serviceMetaInfo} to be inflated.
     * @throws Exception if failed to inflate the {@code serviceMetaInfo}
     */
    public void inflate(final Struct metadata, final ServiceMetaInfo serviceMetaInfo) throws Exception {
        final Value empty = Value.newBuilder().setStringValue("-").build();
        final Value root = Value.newBuilder().setStructValue(metadata).build();
        for (final Map.Entry<String, ServiceNameFormat> entry : fieldNameMapping.entrySet()) {
            final ServiceNameFormat serviceNameFormat = entry.getValue();
            final Object[] values = new String[serviceNameFormat.properties.size()];
            for (int i = 0; i < serviceNameFormat.properties.size(); i++) {
                final List<String> properties = serviceNameFormat.properties.get(i);
                Value value = root;
                for (final String property : properties) {
                    value = value.getStructValue().getFieldsOrDefault(property, empty);
                }
                values[i] = value.getStringValue();
            }
            final String value = Strings.lenientFormat(serviceNameFormat.format, values);
            if (!Strings.isNullOrEmpty(value)) {
                fieldSetterMapping.get(entry.getKey()).invoke(serviceMetaInfo, value);
            }
        }
    }

    @RequiredArgsConstructor
    private static class ServiceNameFormat {
        private final String format;

        private final List<List<String>> properties;
    }
}
