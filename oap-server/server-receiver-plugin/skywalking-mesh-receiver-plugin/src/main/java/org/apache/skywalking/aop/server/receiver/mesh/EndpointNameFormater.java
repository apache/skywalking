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
import java.util.Properties;
import org.apache.skywalking.apm.util.StringFormatGroup;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.slf4j.*;

/**
 * @author wusheng
 */
public class EndpointNameFormater {
    private static final Logger logger = LoggerFactory.getLogger(EndpointNameFormater.class);
    private static StringFormatGroup ENDPOINT_FORMAT_RULE;

    public static void init() {
        ENDPOINT_FORMAT_RULE = new StringFormatGroup();
        Properties properties = new Properties();
        try {
            InputStream stream = ResourceUtils.class.getClassLoader().getResourceAsStream("endpoint_rules.properties");
            if (stream == null) {
                logger.info("endpoint_rules.properties not found. No endpoint name setup.");
                return;
            }
            properties.load(stream);
        } catch (IOException e) {
            logger.info("endpoint_rules.properties not found. No endpoint name setup.");
        }

        properties.forEach((key, value) -> {
            ENDPOINT_FORMAT_RULE.addRule((String)key, (String)value);
        });
    }

    public static StringFormatGroup.FormatResult format(String endpointName) {
        return ENDPOINT_FORMAT_RULE.format(endpointName);
    }
}
