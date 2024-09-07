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

package org.apache.skywalking.oap.server.library.util;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class FieldsHelper {

    /**
     * The difference Class have different {@link FieldsHelper} instance for their own mappings.
     */
    private static final Map<Class<?>, FieldsHelper> HELPER_MAP = new ConcurrentHashMap<>();

    /**
     * The target class to be inflated.
     */
    private final Class<?> targetClass;

    /**
     * Whether the {@link FieldsHelper} has been initialized.
     */
    private boolean initialized = false;

    /**
     * The mappings from the field name to the field name of {@code flatbuffers}.
     */
    private Map<String, FieldFormat> fieldNameMapping;

    /**
     * The mappings from the field name to its {@code setter}.
     */
    private Map<String, BiConsumer<Object, String>> fieldSetterMapping;

    public static FieldsHelper forClass(final Class<?> targetClass) {
        return HELPER_MAP.computeIfAbsent(targetClass, FieldsHelper::new);
    }

    private FieldsHelper(Class<?> targetClass) {
        this.targetClass = targetClass;
    }

    public void init(final String file) throws Exception {
        init(ResourceUtils.readToStream(file));
    }

    public void init(final InputStream inputStream) {
        if (initialized) {
            return;
        }

        final Yaml yaml = new Yaml();
        final Map<String, String> config = yaml.load(inputStream);

        fieldNameMapping = new HashMap<>(config.size());
        fieldSetterMapping = new HashMap<>(config.size());

        for (final Map.Entry<String, String> entry : config.entrySet()) {
            final String serviceMetaInfoFieldName = entry.getKey();
            final String flatBuffersFieldName = entry.getValue();

            final Pattern p = Pattern.compile("(\\$\\{(?<properties>.+?)})");
            final Matcher m = p.matcher(flatBuffersFieldName);
            final List<Property> flatBuffersFieldNames = new ArrayList<>(m.groupCount());
            final StringBuffer serviceNamePattern = new StringBuffer();
            while (m.find()) {
                final String properties = m.group("properties");
                final List<Field> fields = Splitter.on(',').omitEmptyStrings().splitToList(properties).stream().map(candidate -> {
                    List<String> tokens = Splitter.on('.').omitEmptyStrings().splitToList(candidate);

                    StringBuilder tokenBuffer = new StringBuilder();
                    List<String> candidateFields = new ArrayList<>(tokens.size());
                    for (String token : tokens) {
                        if (tokenBuffer.length() == 0 && token.startsWith("\"")) {
                            tokenBuffer.append(token);
                        } else if (tokenBuffer.length() > 0) {
                            tokenBuffer.append(".").append(token);
                        } else {
                            candidateFields.add(token);
                        }

                        if (tokenBuffer.length() > 0 && token.endsWith("\"")) {
                            candidateFields.add(tokenBuffer.toString().replaceAll("\"", ""));
                            tokenBuffer.setLength(0);
                        }
                    }
                    return new Field(candidateFields);
                }).collect(Collectors.toList());
                flatBuffersFieldNames.add(new Property(fields));
                m.appendReplacement(serviceNamePattern, "%s");
            }

            fieldNameMapping.put(
                serviceMetaInfoFieldName,
                new FieldFormat(serviceNamePattern.toString(), flatBuffersFieldNames)
            );

            try {
                final String setter = "set" + StringUtils.capitalize(serviceMetaInfoFieldName);
                final MethodHandles.Lookup lookup = MethodHandles.lookup();
                final Class<?> parameterType = String.class;
                final CallSite site = LambdaMetafactory.metafactory(
                        lookup, "accept",
                        MethodType.methodType(BiConsumer.class),
                        MethodType.methodType(void.class, Object.class, Object.class),
                        lookup.findVirtual(targetClass, setter,
                                           MethodType.methodType(void.class, parameterType)),
                        MethodType.methodType(void.class, targetClass, parameterType));
                final MethodHandle factory = site.getTarget();
                final BiConsumer<Object, String> method =
                        (BiConsumer<Object, String>) factory.invoke();
                fieldSetterMapping.put(serviceMetaInfoFieldName, method);
            } catch (final Throwable e) {
                throw new IllegalStateException("Initialize method error", e);
            }
        }
        initialized = true;
    }

    /**
     * Inflates the {@code target} with the given {@link Struct struct}.
     *
     * @param metadata        the {@link Struct} metadata from where to retrieve and inflate the {@code target}.
     * @param target the {@code target} to be inflated.
     */
    public void inflate(final Struct metadata, final Object target) {
        final Value empty = Value.newBuilder().setStringValue("-").build();
        final Value root = Value.newBuilder().setStructValue(metadata).build();
        for (final Map.Entry<String, FieldFormat> entry : fieldNameMapping.entrySet()) {
            final FieldFormat fieldFormat = entry.getValue();
            final Object[] values = new String[fieldFormat.properties.size()];
            for (int i = 0; i < fieldFormat.properties.size(); i++) {
                values[i] = "-"; // Give it a default value
                final Property property = fieldFormat.properties.get(i);
                for (final Field field : property) {
                    Value value = root;
                    for (final String segment : field.dsvSegments) {
                        value = value.getStructValue().getFieldsOrDefault(segment, empty);
                    }
                    if (Strings.isNullOrEmpty(value.getStringValue()) || "-".equals(value.getStringValue())) {
                        continue;
                    }
                    values[i] = value.getStringValue();
                    break;
                }
            }
            final String value = Strings.lenientFormat(fieldFormat.format, values);
            if (!Strings.isNullOrEmpty(value)) {
                fieldSetterMapping.get(entry.getKey()).accept(target, value);
            }
        }
    }

    @RequiredArgsConstructor
    private static class FieldFormat {
        private final String format;

        private final List<Property> properties;
    }

    /**
     * A property in the metadata map, it may have multiple candidates, of which the first is non empty will be used.
     * For example, to look up the service name, you may set candidates like ${LABELS."service.istio.io/canonical-name",LABELS."app.kubernetes.io/name","app"}.
     */
    @RequiredArgsConstructor
    private static class Property implements Iterable<Field> {
        @Delegate
        private final List<Field> candidateFields;
    }

    /**
     * A field in the property, it may be nested such as LABELS.app, LABELS.revision, etc.
     * {@link #dsvSegments} are the `.` separated segment list, such as ["LABELS", "app"], ["LABELS", "revision"].
     */
    @RequiredArgsConstructor
    private static class Field implements Iterable<String> {
        @Delegate
        private final List<String> dsvSegments;
    }
}
