/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.agent.stream.worker.trace;

import org.skywalking.apm.collector.agent.stream.parser.SegmentParse;
import org.skywalking.apm.collector.agent.stream.service.trace.ITraceSegmentService;
import org.skywalking.apm.collector.core.module.ModuleManager;
import org.skywalking.apm.network.proto.UpstreamSegment;

/**
 * @author peng-yongsheng
 */
public class TraceSegmentService implements ITraceSegmentService {

    private final ModuleManager moduleManager;

    public TraceSegmentService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    public void send(UpstreamSegment segment) {
        SegmentParse segmentParse = new SegmentParse(moduleManager);
        segmentParse.parse(segment, SegmentParse.Source.Agent);
    }
}
