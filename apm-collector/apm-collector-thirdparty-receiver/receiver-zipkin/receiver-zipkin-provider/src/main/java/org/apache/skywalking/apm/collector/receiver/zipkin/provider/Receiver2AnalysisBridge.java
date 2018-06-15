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

package org.apache.skywalking.apm.collector.receiver.zipkin.provider;

import org.apache.skywalking.apm.collector.analysis.segment.parser.define.service.ISegmentParseService;
import org.apache.skywalking.apm.collector.receiver.zipkin.provider.data.SkyWalkingTrace;
import org.apache.skywalking.apm.collector.receiver.zipkin.provider.transform.SegmentListener;
import org.apache.skywalking.apm.collector.receiver.zipkin.provider.transform.Zipkin2SkyWalkingTransfer;

/**
 * Send the segments to Analysis module, like receiving segments from native SkyWalking agents.
 */
public class Receiver2AnalysisBridge implements SegmentListener {
    private ISegmentParseService segmentParseService;

    public Receiver2AnalysisBridge(ISegmentParseService segmentParseService) {
        this.segmentParseService = segmentParseService;
    }

    /**
     * Add this bridge as listener to Zipkin span transfer.
     */
    public void build() {
        Zipkin2SkyWalkingTransfer.INSTANCE.addListener(this);
    }

    @Override
    public void notify(SkyWalkingTrace trace) {
        trace.toUpstreamSegment().forEach(upstream -> segmentParseService.parse(upstream.build(), ISegmentParseService.Source.Agent));

    }
}
