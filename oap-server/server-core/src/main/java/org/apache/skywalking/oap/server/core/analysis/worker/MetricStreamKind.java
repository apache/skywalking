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

package org.apache.skywalking.oap.server.core.analysis.worker;

/**
 * MetricStreamKind represents the kind of metric character.
 */
public enum MetricStreamKind {
    /**
     * Metric built by {@link org.apache.skywalking.oap.server.core.oal.rt.OALEngine}
     *
     * OAL is SkyWalking native metrics from SkyWalking native analyzers, for traces and service mesh logs.
     * The {@link org.apache.skywalking.oap.server.core.source.Source} implementations represent the raw traffic.
     *
     * The significant different between OAL and {@link #MAL} type is, the traffic load of OAL metrics is much more.
     * So,in the stream process, kernel assigned larger buffer and more resources for this kind.
     */
    OAL,
    /**
     * Metric built by {@link org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem}
     *
     * MAL metrics are from existing metric system, such as SkyWalking meter, Prometheus, OpenTelemetry
     */
    MAL
}
