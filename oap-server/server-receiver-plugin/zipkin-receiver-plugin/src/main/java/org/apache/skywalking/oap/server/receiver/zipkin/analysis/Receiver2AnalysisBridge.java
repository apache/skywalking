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

package org.apache.skywalking.oap.server.receiver.zipkin.analysis;

import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.ISegmentParserService;
import org.apache.skywalking.oap.server.receiver.zipkin.analysis.data.SkyWalkingTrace;
import org.apache.skywalking.oap.server.receiver.zipkin.analysis.transform.SegmentListener;

/**
 * Send the segments to Analysis module, like receiving segments from native SkyWalking agents.
 */
public class Receiver2AnalysisBridge implements SegmentListener {
    private ISegmentParserService segmentParseService;

    public Receiver2AnalysisBridge(ISegmentParserService segmentParseService) {
        this.segmentParseService = segmentParseService;
    }

    @Override
    public void notify(SkyWalkingTrace trace) {
        trace.getSegmentList().forEach(upstream -> segmentParseService.send(upstream.build()));

    }
}
