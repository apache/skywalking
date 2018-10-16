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

package org.apache.skywalking.oap.server.receiver.trace.provider.handler.v5.rest.reader;

import com.google.gson.stream.JsonReader;
import java.io.IOException;
import org.apache.skywalking.apm.network.language.agent.LogMessage;

/**
 * @author peng-yongsheng
 */
public class LogJsonReader implements StreamJsonReader<LogMessage> {

    private KeyWithStringValueJsonReader keyWithStringValueJsonReader = new KeyWithStringValueJsonReader();

    private static final String TIME = "ti";
    private static final String LOG_DATA = "ld";

    @Override public LogMessage read(JsonReader reader) throws IOException {
        LogMessage.Builder builder = LogMessage.newBuilder();

        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case TIME:
                    builder.setTime(reader.nextLong());
                    break;
                case LOG_DATA:
                    reader.beginArray();
                    while (reader.hasNext()) {
                        builder.addData(keyWithStringValueJsonReader.read(reader));
                    }
                    reader.endArray();
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();

        return builder.build();
    }
}
