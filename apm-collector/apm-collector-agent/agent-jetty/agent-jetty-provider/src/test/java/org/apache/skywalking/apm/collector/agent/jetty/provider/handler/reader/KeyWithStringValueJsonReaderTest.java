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
import com.google.gson.JsonObject;
import org.apache.skywalking.apm.network.proto.KeyWithStringValue;
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
public class KeyWithStringValueJsonReaderTest extends BaseReader {

    private KeyWithStringValueJsonReader jsonReader;

    @Before
    public void setUp() throws Exception {
        jsonReader = new KeyWithStringValueJsonReader();
    }

    @Test
    public void read() throws IOException {
        Gson gson = new Gson();
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("k", "hello");
        jsonObject.addProperty("v", "world");
        KeyWithStringValue read = jsonReader.read(getReader(jsonObject));
        assertEquals(read.getKey(),"hello");
        assertEquals(read.getValue(),"world");
    }
}