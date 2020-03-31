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

package org.apache.skywalking.oap.server.core.analysis.manual.service;

import org.apache.skywalking.oap.server.core.analysis.MetricsExtension;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.ScopeDeclaration;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.SERVICE_TRAFFIC;

@ScopeDeclaration(id = SERVICE_TRAFFIC, name = "ServiceTraffic")
@Stream(name = ServiceTraffic.INDEX_NAME, scopeId = DefaultScopeDefine.SERVICE_TRAFFIC,
    builder = EndpointTraffic.Builder.class, processor = MetricsStreamProcessor.class)
@MetricsExtension(supportDownSampling = false, supportUpdate = false)
public class ServiceTraffic extends Metrics {
    public static final String INDEX_NAME = "service_traffic";

    @Override
    public void combine(final Metrics metrics) {

    }

    @Override
    public void calculate() {

    }

    @Override
    public Metrics toHour() {
        return null;
    }

    @Override
    public Metrics toDay() {
        return null;
    }

    @Override
    public Metrics toMonth() {
        return null;
    }

    @Override
    public int remoteHashCode() {
        return 0;
    }

    @Override
    public void deserialize(final RemoteData remoteData) {

    }

    @Override
    public RemoteData.Builder serialize() {
        return null;
    }

    @Override
    public String id() {
        return null;
    }
}
