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

package org.apache.skywalking.oap.server.core.source;

import org.apache.skywalking.apm.network.language.agent.SpanType;

/**
 * @author peng-yongsheng
 */
public enum DetectPoint {
    SERVER, CLIENT, PROXY, UNRECOGNIZED;

    public static DetectPoint fromSpanType(SpanType spanType) {
        switch (spanType) {
            case Entry:
                return DetectPoint.SERVER;
            case Exit:
                return DetectPoint.CLIENT;
            case UNRECOGNIZED:
            case Local:
            default:
                return DetectPoint.UNRECOGNIZED;
        }
    }

    public static DetectPoint fromNetworkProtocolDetectPoint(org.apache.skywalking.apm.network.common.DetectPoint detectPoint) {
        switch (detectPoint) {
            case client:
                return CLIENT;
            case server:
                return SERVER;
            case proxy:
            case UNRECOGNIZED:
            default:
                return UNRECOGNIZED;
        }
    }
}
