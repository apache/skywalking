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

package org.apache.skywalking.oap.server.analyzer.provider.meter.process;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.skywalking.apm.network.language.agent.v3.MeterData;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Process meter when receive the meter data.
 */
@Slf4j
public class MeterProcessor {

    /**
     * Process context.
     */
    private final MeterProcessService processService;

    /**
     * All of meters has been read. Using it to process groovy script.
     */
    private final Map<String, EvalMultipleData> meters = new HashMap<>();

    /**
     * Agent service name.
     */
    private String service;

    /**
     * Agent service instance name.
     */
    private String serviceInstance;

    /**
     * Agent send time.
     */
    private Long timestamp;

    public MeterProcessor(MeterProcessService processService) {
        this.processService = processService;
    }

    public void read(MeterData data) {
        // Parse to eval data
        EvalData evalData;
        switch (data.getMetricCase()) {
            case SINGLEVALUE:
                evalData = EvalSingleData.build(data.getSingleValue(), this);
                break;
            case HISTOGRAM:
                evalData = EvalHistogramData.build(data.getHistogram(), this);
                break;
            default:
                return;
        }

        // Save meter
        final EvalMultipleData multipleEvalData = meters.computeIfAbsent(evalData.getName(), k -> new EvalMultipleData(k));
        multipleEvalData.appendData(evalData);

        // Agent info
        if (StringUtil.isNotEmpty(data.getService())) {
            service = data.getService();
        }
        if (StringUtil.isNotEmpty(data.getServiceInstance())) {
            serviceInstance = data.getServiceInstance();
        }
        if (data.getTimestamp() > 0) {
            timestamp = data.getTimestamp();
        }
    }

    /**
     * Process all of meters and send to meter system.
     */
    public void process() {
        // Check agent information
        if (StringUtils.isEmpty(service) || StringUtil.isEmpty(serviceInstance) || timestamp == null) {
            return;
        }

        // Get all meter builders.
        final List<MeterBuilder> enabledBuilders = processService.enabledBuilders();
        if (CollectionUtils.isEmpty(enabledBuilders)) {
            return;
        }

        try {
            // Init groovy shell
            final Binding binding = new Binding();
            binding.setVariable("meter", new BindingMeterMap(meters));
            final GroovyShell shell = new GroovyShell(binding);

            // Build meter and send
            for (MeterBuilder builder : enabledBuilders) {
                builder.buildAndSend(this, shell);
            }
        } catch (Exception e) {
            log.warn("Process meters failure.", e);
        }
    }

    /**
     * Agent service name
     */
    String service() {
        return service;
    }

    /**
     * Agent service instance name
     */
    String serviceInstance() {
        return serviceInstance;
    }

    /**
     * Agent send time
     * @return
     */
    Long timestamp() {
        return timestamp;
    }

    /**
     * Current agent window.
     */
    Window window() {
        return Window.getWindow(service(), serviceInstance());
    }

    /**
     * Wrapper the meter map, If could not found the meter, It will throw a easy to identity exception, not the NPE.
     */
    private static class BindingMeterMap implements Map<String, EvalMultipleData> {
        private final Map<String, EvalMultipleData> data;

        public BindingMeterMap(Map<String, EvalMultipleData> data) {
            this.data = data;
        }

        @Override
        public int size() {
            return data.size();
        }

        @Override
        public boolean isEmpty() {
            return data.isEmpty();
        }

        @Override
        public boolean containsKey(Object key) {
            return data.containsKey(key);
        }

        @Override
        public boolean containsValue(Object value) {
            return data.containsValue(value);
        }

        @Override
        public EvalMultipleData get(Object key) {
            final EvalMultipleData data = this.data.get(key);
            if (data == null) {
                throw new IllegalArgumentException("Could not found meter: " + key);
            }
            return data;
        }

        @Override
        public EvalMultipleData put(String key, EvalMultipleData value) {
            return data.put(key, value);
        }

        @Override
        public EvalMultipleData remove(Object key) {
            return data.remove(key);
        }

        @Override
        public void putAll(Map<? extends String, ? extends EvalMultipleData> m) {
            data.putAll(m);
        }

        @Override
        public void clear() {
            data.clear();
        }

        @Override
        public Set<String> keySet() {
            return data.keySet();
        }

        @Override
        public Collection<EvalMultipleData> values() {
            return data.values();
        }

        @Override
        public Set<Entry<String, EvalMultipleData>> entrySet() {
            return data.entrySet();
        }
    }
}
