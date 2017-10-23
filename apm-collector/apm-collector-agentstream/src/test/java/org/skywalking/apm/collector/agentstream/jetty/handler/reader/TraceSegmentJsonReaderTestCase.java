/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.agentstream.jetty.handler.reader;

import com.google.gson.JsonElement;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.StringReader;
import org.junit.Test;
import org.skywalking.apm.collector.agentstream.mock.JsonFileReader;

/**
 * @author peng-yongsheng
 */
public class TraceSegmentJsonReaderTestCase {

    @Test
    public void testRead() throws IOException {
        TraceSegmentJsonReader reader = new TraceSegmentJsonReader();
        JsonElement jsonElement = JsonFileReader.INSTANCE.read("json/segment/normal/dubbox-consumer.json");

        JsonReader jsonReader = new JsonReader(new StringReader(jsonElement.toString()));
        jsonReader.beginArray();
        while (jsonReader.hasNext()) {
            reader.read(jsonReader);
        }
        jsonReader.endArray();
    }
}
