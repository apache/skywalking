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

package org.apache.skywalking.apm.collector.analysis.segment.parser.provider.service;

import org.apache.skywalking.apm.collector.analysis.segment.parser.define.service.ISegmentParseService;
import org.apache.skywalking.apm.collector.analysis.segment.parser.provider.parser.SegmentParse;
import org.apache.skywalking.apm.collector.analysis.segment.parser.provider.parser.SegmentParserListenerManager;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.network.proto.UpstreamSegment;

/**
 * @author peng-yongsheng
 */
public class SegmentParseService implements ISegmentParseService {

    private final ModuleManager moduleManager;
    private final SegmentParserListenerManager listenerManager;

    public SegmentParseService(ModuleManager moduleManager, SegmentParserListenerManager listenerManager) {
        this.moduleManager = moduleManager;
        this.listenerManager = listenerManager;
    }

    @Override public void parse(UpstreamSegment segment, Source source) {
        SegmentParse segmentParse = new SegmentParse(moduleManager, listenerManager);
        segmentParse.parse(segment, source);
    }
}
