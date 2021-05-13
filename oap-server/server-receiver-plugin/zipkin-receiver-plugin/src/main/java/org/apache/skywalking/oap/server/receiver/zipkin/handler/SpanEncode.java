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

package org.apache.skywalking.oap.server.receiver.zipkin.handler;

public class SpanEncode {
    public static final int PROTO3 = 1;
    public static final int JSON_V2 = 2;
    public static final int THRIFT = 3;
    public static final int JSON_V1 = 4;

    public static boolean isProto3(int encode) {
        return PROTO3 == encode;
    }

    public static boolean isJsonV2(int encode) {
        return JSON_V2 == encode;
    }

    public static boolean isThrift(int encode) {
        return THRIFT == encode;
    }

    public static boolean isJsonV1(int encode) {
        return JSON_V1 == encode;
    }
}
