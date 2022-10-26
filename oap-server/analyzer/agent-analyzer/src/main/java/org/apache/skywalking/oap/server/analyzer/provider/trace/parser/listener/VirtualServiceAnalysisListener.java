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

package org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener;

import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.oap.server.analyzer.provider.AnalyzerModuleConfig;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.vservice.VirtualCacheProcessor;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.vservice.VirtualDatabaseProcessor;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.vservice.VirtualMQProcessor;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.vservice.VirtualServiceProcessor;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * Virtual Service represent remote service
 */

@RequiredArgsConstructor
public class VirtualServiceAnalysisListener implements ExitAnalysisListener, LocalAnalysisListener, EntryAnalysisListener {

    private final SourceReceiver sourceReceiver;
    private final List<VirtualServiceProcessor> virtualServiceProcessors;

    @Override
    public void build() {
        virtualServiceProcessors.forEach(p -> p.emitTo(sourceReceiver::receive));
    }

    @Override
    public boolean containsPoint(Point point) {
        return point == Point.Local || point == Point.Exit || point == Point.Entry;
    }

    @Override
    public void parseExit(SpanObject span, SegmentObject segmentObject) {
        virtualServiceProcessors.forEach(p -> p.prepareVSIfNecessary(span, segmentObject));
    }

    @Override
    public void parseLocal(SpanObject span, SegmentObject segmentObject) {
        virtualServiceProcessors.forEach(p -> p.prepareVSIfNecessary(span, segmentObject));
    }

    @Override
    public void parseEntry(final SpanObject span, final SegmentObject segmentObject) {
        virtualServiceProcessors.forEach(p -> p.prepareVSIfNecessary(span, segmentObject));
    }

    public static class Factory implements AnalysisListenerFactory {
        private final SourceReceiver sourceReceiver;
        private final NamingControl namingControl;

        public Factory(ModuleManager moduleManager) {
            this.sourceReceiver = moduleManager.find(CoreModule.NAME).provider().getService(SourceReceiver.class);
            this.namingControl = moduleManager.find(CoreModule.NAME)
                    .provider()
                    .getService(NamingControl.class);
        }

        @Override
        public AnalysisListener create(ModuleManager moduleManager, AnalyzerModuleConfig config) {
            return new VirtualServiceAnalysisListener(
                sourceReceiver,
                Arrays.asList(
                    new VirtualCacheProcessor(namingControl, config),
                    new VirtualDatabaseProcessor(namingControl, config),
                    new VirtualMQProcessor(namingControl)
                )
            );
        }
    }

}

