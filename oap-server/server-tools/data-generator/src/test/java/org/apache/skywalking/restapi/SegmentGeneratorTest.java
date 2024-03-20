/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.skywalking.restapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static graphql.Assert.assertFalse;
import static graphql.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SegmentGeneratorTest {

    @Test
    void next() throws URISyntaxException, IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        URL url = getClass().getClassLoader().getResource("segment.tpl.json");
        assertNotNull(url);
        File jsonFile = new File(url.toURI());
        SegmentRequest sr = objectMapper.readValue(jsonFile, SegmentRequest.class);
        sr.init();
        Set<String> serviceSet = new HashSet<>();
        Set<String> serviceInstanceSet = new HashSet<>();
        Set<String> endpointSet = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            List<SegmentGenerator.SegmentResult> ss = sr.next(null);
            assertFalse(ss.isEmpty());
            for (SegmentGenerator.SegmentResult s : ss) {
                serviceSet.add(s.segmentObject.getService());
                serviceInstanceSet.add(s.segmentObject.getServiceInstance());
                endpointSet.add(s.segment.getEndpointId());
            }
        }
        assertTrue(serviceSet.size() > 1);
        assertTrue(serviceSet.size() <= 10);
        assertTrue(serviceInstanceSet.size() > 1);
        assertTrue(serviceInstanceSet.size() <= 100);
        assertTrue(endpointSet.size() > 1);
        assertTrue(endpointSet.size() <= 100);
    }
}