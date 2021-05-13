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

package org.apache.skywalking.e2e;

import java.io.IOException;
import java.io.InputStream;
import org.apache.skywalking.e2e.trace.Trace;
import org.apache.skywalking.e2e.trace.TraceMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestMatcher {
    private TraceMatcher traceMatcher;

    @BeforeEach
    public void setUp() throws IOException {
        final InputStream expectedInputStream = new ClassPathResource("test.yml").getInputStream();
        traceMatcher = new Yaml().loadAs(expectedInputStream, TraceMatcher.class);
    }

    @Test
    public void shouldSuccess() {
        final Trace trace = new Trace().setKey("abc").setStart("1").setError(false);
        trace.getEndpointNames().add("e2e/test");
        trace.getTraceIds().add("id1");
        trace.getTraceIds().add("id2");
        traceMatcher.verify(trace);
    }

    @Test
    public void shouldVerifyNotNull() {
        assertThrows(AssertionError.class, () -> {
            final Trace trace = new Trace().setStart("1").setError(false);
            trace.getEndpointNames().add("e2e/test");
            trace.getTraceIds().add("id1");
            trace.getTraceIds().add("id2");
            traceMatcher.verify(trace);
        });
    }

    @Test
    public void shouldVerifyGreaterOrEqualTo() {
        assertThrows(AssertionError.class, () -> {
            final Trace trace = new Trace().setKey("abc").setDuration(-1).setStart("1").setError(false);
            trace.getEndpointNames().add("e2e/test");
            trace.getTraceIds().add("id1");
            trace.getTraceIds().add("id2");
            traceMatcher.verify(trace);
        });
    }

    @Test
    public void shouldVerifyGreaterThan() {
        assertThrows(AssertionError.class, () -> {
            final Trace trace = new Trace().setKey("abc").setDuration(1).setStart("0").setError(false);
            trace.getEndpointNames().add("e2e/test");
            trace.getTraceIds().add("id1");
            trace.getTraceIds().add("id2");
            traceMatcher.verify(trace);
        });
    }
}
