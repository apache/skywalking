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

package org.apache.skywalking.oap.server.exporter.provider.grpc;

import org.apache.skywalking.oap.server.core.analysis.indicator.Indicator;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;

/**
 * Created by dengming, 2019.04.20
 */
public class MockIndicator extends Indicator {

    @Override
    public String id() {
        return "mock-indicator";
    }

    @Override
    public void combine(Indicator indicator) {

    }

    @Override
    public void calculate() {

    }

    @Override
    public Indicator toHour() {
        return this;
    }

    @Override
    public Indicator toDay() {
        return this;
    }

    @Override
    public Indicator toMonth() {
        return this;
    }

    @Override
    public int remoteHashCode() {
        return 1;
    }

    @Override
    public void deserialize(RemoteData remoteData) {

    }

    @Override
    public RemoteData.Builder serialize() {
        return null;
    }
}
