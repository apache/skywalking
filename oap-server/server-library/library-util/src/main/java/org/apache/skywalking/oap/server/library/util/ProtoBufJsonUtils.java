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

package org.apache.skywalking.oap.server.library.util;

import com.google.protobuf.BytesValue;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;

public class ProtoBufJsonUtils {

    public static String toJSON(Message sourceMessage) throws IOException {
        return JsonFormat.printer()
                         .usingTypeRegistry(
                             JsonFormat
                                 .TypeRegistry
                                 .newBuilder()
                                 .add(BytesValue.getDescriptor())
                                 .build()
                         )
                         .print(sourceMessage);
    }

    /**
     * Extract data from a JSON String and use them to construct a Protocol Buffers Message.
     *
     * @param json          A JSON data string to parse
     * @param targetBuilder A Message builder to use to construct the resulting Message
     * @throws com.google.protobuf.InvalidProtocolBufferException Thrown in case of invalid Message data
     */
    public static void fromJSON(String json, Message.Builder targetBuilder) throws IOException {
        JsonFormat.parser()
                  .usingTypeRegistry(
                      JsonFormat.TypeRegistry.newBuilder()
                                             .add(targetBuilder.getDescriptorForType())
                                             .build())
                  .ignoringUnknownFields()
                  .merge(json, targetBuilder);
    }
}
