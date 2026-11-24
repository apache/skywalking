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

package org.apache.skywalking.oap.server.admin.dsl.debugging.cluster;

import org.apache.skywalking.oap.server.admin.dsl.debugging.cluster.v1.ClusterCaptureTime;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.CaptureTime;

/**
 * A capture time as the nodes exchange it.
 */
public final class CaptureTimeCodec {
    private CaptureTimeCodec() {
    }

    public static ClusterCaptureTime encode(final CaptureTime time) {
        return ClusterCaptureTime.newBuilder().setMillis(time.getMillis()).setNanos(time.getNanos()).build();
    }

    public static CaptureTime decode(final ClusterCaptureTime time) {
        return new CaptureTime(time.getMillis(), time.getNanos());
    }
}
