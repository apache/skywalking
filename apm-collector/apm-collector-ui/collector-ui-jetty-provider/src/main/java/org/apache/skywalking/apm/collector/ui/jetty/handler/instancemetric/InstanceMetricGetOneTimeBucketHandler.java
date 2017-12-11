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


package org.apache.skywalking.apm.collector.ui.jetty.handler.instancemetric;

import com.google.gson.JsonElement;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.server.jetty.ArgumentsParseException;
import org.apache.skywalking.apm.collector.server.jetty.JettyHandler;
import org.apache.skywalking.apm.collector.ui.service.InstanceJVMService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class InstanceMetricGetOneTimeBucketHandler extends JettyHandler {

    private final Logger logger = LoggerFactory.getLogger(InstanceMetricGetOneTimeBucketHandler.class);

    @Override public String pathSpec() {
        return "/instance/jvm/instanceId/oneBucket";
    }

    private final InstanceJVMService service;

    public InstanceMetricGetOneTimeBucketHandler(ModuleManager moduleManager) {
        this.service = new InstanceJVMService(moduleManager);
    }

    @Override protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        String timeBucketStr = req.getParameter("timeBucket");
        String instanceIdStr = req.getParameter("instanceId");
        String[] metricTypes = req.getParameterValues("metricTypes");

        logger.debug("instance jvm metric get timeBucket: {}, instance id: {}, metric types: {}", timeBucketStr, instanceIdStr, metricTypes);

        long timeBucket;
        try {
            timeBucket = Long.parseLong(timeBucketStr);
        } catch (NumberFormatException e) {
            throw new ArgumentsParseException("timeBucket must be long");
        }

        int instanceId;
        try {
            instanceId = Integer.parseInt(instanceIdStr);
        } catch (NumberFormatException e) {
            throw new ArgumentsParseException("instance id must be integer");
        }

        if (metricTypes.length == 0) {
            throw new ArgumentsParseException("at least one metric type");
        }

        Set<String> metricTypeSet = new LinkedHashSet<>();
        for (String metricType : metricTypes) {
            metricTypeSet.add(metricType);
        }

        return service.getInstanceJvmMetric(instanceId, metricTypeSet, timeBucket);
    }

    @Override protected JsonElement doPost(HttpServletRequest req) throws ArgumentsParseException {
        throw new UnsupportedOperationException();
    }
}
