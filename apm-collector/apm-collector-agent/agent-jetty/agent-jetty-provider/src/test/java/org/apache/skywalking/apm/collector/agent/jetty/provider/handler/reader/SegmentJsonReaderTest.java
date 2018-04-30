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
import org.apache.skywalking.apm.network.proto.TraceSegmentObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;

import static org.junit.Assert.assertTrue;

/**
 * @author lican
 */
@RunWith(MockitoJUnitRunner.class)
public class SegmentJsonReaderTest extends BaseReader {

    private SegmentJsonReader segmentJsonReader;

    @Before
    public void setUp() throws Exception {
        segmentJsonReader = new SegmentJsonReader();
    }

    /**
     * { //TraceSegmentObject
     * "ts": [137150, 185809, 48780000],
     * "ai": 2, //applicationId
     * "ii": 3, //applicationInstanceId
     * "ss": []//SpanObject
     */
    @Test
    public void read() throws IOException {
        JsonArray tsArray = new JsonArray();
        tsArray.add(137150);
        tsArray.add(185809);
        tsArray.add(48780000);

        JsonObject json = new JsonObject();
        json.add("ts", tsArray);
        json.addProperty("ai", 2);
        json.addProperty("ii", 3);
        json.add("ss", new JsonArray());
        TraceSegmentObject.Builder read = segmentJsonReader.read(getReader(json));
        TraceSegmentObject build = read.build();
        assertTrue(build.getTraceSegmentId().getIdPartsList().size() == 3);
    }
}