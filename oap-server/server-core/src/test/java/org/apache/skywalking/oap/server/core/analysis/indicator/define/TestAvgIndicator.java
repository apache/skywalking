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

package org.apache.skywalking.oap.server.core.analysis.indicator.define;

import java.util.Map;
import lombok.*;
import org.apache.skywalking.oap.server.core.analysis.indicator.*;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;

/**
 * @author peng-yongsheng
 */
public class TestAvgIndicator extends AvgIndicator {

    @Setter @Getter private int id;

    @Override public RemoteData.Builder serialize() {
        return null;
    }

    @Override public String name() {
        return null;
    }

    @Override public void deserialize(RemoteData remoteData) {
    }

    @Override public String id() {
        return null;
    }

    @Override public Map<String, Object> toMap() {
        return null;
    }

    @Override public Indicator newOne(Map<String, Object> dbMap) {
        return null;
    }
}
