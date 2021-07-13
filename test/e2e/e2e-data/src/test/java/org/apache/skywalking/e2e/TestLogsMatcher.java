/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.e2e;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.skywalking.e2e.common.KeyValue;
import org.apache.skywalking.e2e.log.Log;
import org.apache.skywalking.e2e.log.LogsMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

public class TestLogsMatcher {

    private LogsMatcher logsMatcher;

    @BeforeEach
    public void setUp() throws IOException {
        try (InputStream expectedInputStream = new ClassPathResource("log.yml").getInputStream()) {
            logsMatcher = new Yaml().loadAs(expectedInputStream, LogsMatcher.class);
        }
    }

    @Test
    public void shouldSuccess() {
        final List<Log> logs = new ArrayList<>();
        logs.add(new Log().setServiceName("e2e")
                          .setServiceId("ZTJl.1")
                          .setServiceInstanceName("e2e-instance")
                          .setServiceInstanceId("ZTJl.1_ZTJlLWluc3RhbmNl")
                          .setEndpointName("/traffic")
                          .setEndpointId("ZTJl.1_L3RyYWZmaWM=")
                          .setTraceId("ac81b308-0d66-4c69-a7af-a023a536bd3e")
                          .setTimestamp(1609665785987L)
                          .setContentType("TEXT")
                          .setContent("log")
                          .setTags(
                              Collections.singletonList(new KeyValue().setKey("key").setValue("value"))));
        logs.add(new Log().setServiceName("e2e")
                          .setServiceId("ZTJl.1")
                          .setServiceInstanceName("e2e-instance")
                          .setServiceInstanceId("ZTJl.1_ZTJlLWluc3RhbmNl")
                          .setEndpointName("/traffic")
                          .setEndpointId("ZTJl.1_L3RyYWZmaWM=")
                          .setTraceId("ac81b308-0d66-4c69-a7af-a023a536bd3e")
                          .setTimestamp(1609665785987L)
                          .setContentType("TEXT")
                          .setContent("log")
                          .setTags(
                              Collections.singletonList(new KeyValue().setKey("key").setValue("value"))));

        logsMatcher.verifyLoosely(logs);
    }

}
