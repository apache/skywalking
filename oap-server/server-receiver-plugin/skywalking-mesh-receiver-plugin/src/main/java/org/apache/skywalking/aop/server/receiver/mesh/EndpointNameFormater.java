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

package org.apache.skywalking.aop.server.receiver.mesh;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.skywalking.apm.util.StringFormatGroup;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.slf4j.*;

/**
 * @author wusheng
 */
public class EndpointNameFormater {
    private static final Logger logger = LoggerFactory.getLogger(EndpointNameFormater.class);
    private static Map<String, StringFormatGroup> ALL_RULES = new ConcurrentHashMap<>();

    private static void init(String service) {
        if (ALL_RULES.containsKey(service)) {
            return;
        }
        StringFormatGroup endpointRule = new StringFormatGroup();
        Properties properties = new Properties();
        try {
            InputStream stream = ResourceUtils.class.getClassLoader().getResourceAsStream(service + "_endpoint_naming_rules.properties");
            if (stream == null) {
                logger.info("{}_endpoint_naming_rules.properties not found. Try to find global endpoint rule file.", service);
                stream = ResourceUtils.class.getClassLoader().getResourceAsStream("endpoint_naming_rules.properties");
            }

            if (stream == null) {
                logger.info("endpoint_naming_rules.properties not found. No endpoint name setup.");
            } else {
                properties.load(stream);
                properties.forEach((key, value) -> {
                    endpointRule.addRule((String)key, (String)value);
                });
            }
        } catch (IOException e) {
            logger.info("{}_endpoint_rules.properties not found. No endpoint name setup.", service);
        }

        ALL_RULES.put(service, endpointRule);
    }

    public static StringFormatGroup.FormatResult format(String service, String endpointName) {
        init(service);
        return ALL_RULES.get(service).format(endpointName);
    }
}
