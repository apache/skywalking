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
import org.apache.skywalking.apm.network.proto.SpanObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * @author lican
 */
@RunWith(MockitoJUnitRunner.class)
public class SpanJsonReaderTest extends BaseReader {

    private SpanJsonReader spanJsonReader;

    @Before
    public void setUp() throws Exception {
        spanJsonReader = new SpanJsonReader();
    }

    /**
     * {
     * "si": 0, //spanId
     * "tv": 0, //SpanType
     * "lv": 2, //SpanLayer
     * "ps": -1, //parentSpanId
     * "st": 1501858094726, //startTime
     * "et": 1501858096804, //endTime
     * "ci": 3, //componentId
     * "cn": "", //component
     * "oi": 0, //operationNameId
     * "on": "org.skywaking.apm.testcase.dubbo.services.GreetService.doBusiness()", //operationName
     * "pi": 0, //peerId
     * "pn": "", //peer
     * "ie": false, //isError
     * "rs": [ //TraceSegmentReference],
     * "to": [ //KeyWithStringValue ],
     * "lo": [] //log
     * }
     */
    @Test
    public void read() throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("si", 1);
        json.addProperty("tv", 0);
        json.addProperty("lv", 2);
        json.addProperty("ps", -1);
        json.addProperty("st", 1501858094726L);
        json.addProperty("et", 1501858096804L);
        json.addProperty("ci", 3);
        json.addProperty("cn", "redis");
        json.addProperty("oi", 0);
        json.addProperty("on", "org.skywaking.apm.testcase.dubbo.services.GreetService.doBusiness()");
        json.addProperty("pi", 0);
        json.addProperty("pn", "127.0.0.1:6379");
        json.addProperty("ie", false);
        json.add("rs", new JsonArray());
        json.add("to", new JsonArray());
        json.add("lo", new JsonArray());
        SpanObject read = spanJsonReader.read(getReader(json));
        assertEquals(read.getSpanId(), 1);
    }
}