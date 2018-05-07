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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.skywalking.apm.network.proto.TraceSegmentReference;
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
public class ReferenceJsonReaderTest extends BaseReader {

    private ReferenceJsonReader referenceJsonReader;

    @Before
    public void setUp() throws Exception {
        referenceJsonReader = new ReferenceJsonReader();
    }

    /**
     * {
     * "pts": [230150, 185809, 24040000], //parentTraceSegmentId
     * "pii": 2, //parentApplicationInstanceId
     * "psp": 1, //parentSpanId
     * "psi": 0, //parentServiceId
     * "psn": "/dubbox-case/case/dubbox-rest", //parentServiceName
     * "ni": 0,  //networkAddressId
     * "nn": "172.25.0.4:20880", //networkAddress
     * "eii": 2, //entryApplicationInstanceId
     * "esi": 0, //entryServiceId
     * "esn": "/dubbox-case/case/dubbox-rest", //entryServiceName
     * "rv": 0 //RefType
     * }
     */
    @Test
    public void read() throws IOException {
        Gson gson = new Gson();
        JsonObject jsonObject = new JsonObject();
        JsonArray ptsArray = new JsonArray();
        ptsArray.add(230150L);
        ptsArray.add(185809L);
        ptsArray.add(24040000L);

        jsonObject.add("pts", ptsArray);
        jsonObject.addProperty("pii", 2);
        jsonObject.addProperty("psp", 1);
        jsonObject.addProperty("psi", 0);
        jsonObject.addProperty("psn", "/dubbox-case/case/dubbox-rest");
        jsonObject.addProperty("ni", 0);
        jsonObject.addProperty("nn", "172.25.0.4:20880");
        jsonObject.addProperty("eii", 2);
        jsonObject.addProperty("esi", 0);
        jsonObject.addProperty("esn", "/dubbox-case/case/dubbox-rest");
//        jsonObject.addProperty("rn", 0);
        //add
        jsonObject.addProperty("rv", 1);

        TraceSegmentReference read = referenceJsonReader.read(getReader(jsonObject));
        assertTrue(read.getParentTraceSegmentId().getIdPartsList().size() == 3);
    }
}