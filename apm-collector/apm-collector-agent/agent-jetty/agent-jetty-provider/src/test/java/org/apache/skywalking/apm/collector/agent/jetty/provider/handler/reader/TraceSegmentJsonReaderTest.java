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
 */

package org.apache.skywalking.apm.collector.agent.jetty.provider.handler.reader;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.skywalking.apm.network.proto.UniqueId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * @author lican
 */
@RunWith(MockitoJUnitRunner.class)
public class TraceSegmentJsonReaderTest extends BaseReader {

    private TraceSegmentJsonReader traceSegmentJsonReader;

    @Before
    public void setUp() throws Exception {
        traceSegmentJsonReader = new TraceSegmentJsonReader();
    }

    /**
     * {
     * "gt": [[230150, 185809, 24040000]],
     * "sg": { }//TraceSegmentObject
     */
    @Test
    public void read() throws IOException {
        JsonArray array = new JsonArray();
        array.add(230150);
        array.add(185809);
        array.add(24040000);
        JsonArray gtArray = new JsonArray();
        gtArray.add(array);
        JsonObject json = new JsonObject();
        json.add("gt", gtArray);
        json.add("sg", new JsonObject());
        TraceSegment read = traceSegmentJsonReader.read(getReader(json));
        List<UniqueId> globalTraceIdsList = read.getUpstreamSegment().getGlobalTraceIdsList();
        assertTrue(globalTraceIdsList.size() == 1);
    }
}