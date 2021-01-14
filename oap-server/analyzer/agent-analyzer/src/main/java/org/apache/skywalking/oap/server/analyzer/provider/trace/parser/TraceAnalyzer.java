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

package org.apache.skywalking.oap.server.analyzer.provider.trace.parser;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanType;
import org.apache.skywalking.oap.server.analyzer.provider.AnalyzerModuleConfig;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.AnalysisListener;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.EntryAnalysisListener;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.ExitAnalysisListener;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.FirstAnalysisListener;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.LocalAnalysisListener;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.SegmentListener;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

@Slf4j
@RequiredArgsConstructor
public class TraceAnalyzer {
    private final ModuleManager moduleManager;
    private final SegmentParserListenerManager listenerManager;
    private final AnalyzerModuleConfig config;
    private List<AnalysisListener> analysisListeners = new ArrayList<>();

    public void doAnalysis(SegmentObject segmentObject) {
        if (segmentObject.getSpansList().size() == 0) {
            return;
        }

        createSpanListeners();

        notifySegmentListener(segmentObject);

        segmentObject.getSpansList().forEach(spanObject -> {
            if (spanObject.getSpanId() == 0) {
                notifyFirstListener(spanObject, segmentObject);
            }

            if (SpanType.Exit.equals(spanObject.getSpanType())) {
                notifyExitListener(spanObject, segmentObject);
            } else if (SpanType.Entry.equals(spanObject.getSpanType())) {
                notifyEntryListener(spanObject, segmentObject);
            } else if (SpanType.Local.equals(spanObject.getSpanType())) {
                notifyLocalListener(spanObject, segmentObject);
            } else {
                log.error("span type value was unexpected, span type name: {}", spanObject.getSpanType()
                                                                                          .name());
            }
        });

        notifyListenerToBuild();
    }

    private void notifyListenerToBuild() {
        analysisListeners.forEach(AnalysisListener::build);
    }

    private void notifyExitListener(SpanObject span, SegmentObject segmentObject) {
        analysisListeners.forEach(listener -> {
            if (listener.containsPoint(AnalysisListener.Point.Exit)) {
                ((ExitAnalysisListener) listener).parseExit(span, segmentObject);
            }
        });
    }

    private void notifyEntryListener(SpanObject span, SegmentObject segmentObject) {
        analysisListeners.forEach(listener -> {
            if (listener.containsPoint(AnalysisListener.Point.Entry)) {
                ((EntryAnalysisListener) listener).parseEntry(span, segmentObject);
            }
        });
    }

    private void notifyLocalListener(SpanObject span, SegmentObject segmentObject) {
        analysisListeners.forEach(listener -> {
            if (listener.containsPoint(AnalysisListener.Point.Local)) {
                ((LocalAnalysisListener) listener).parseLocal(span, segmentObject);
            }
        });
    }

    private void notifyFirstListener(SpanObject span, SegmentObject segmentObject) {
        analysisListeners.forEach(listener -> {
            if (listener.containsPoint(AnalysisListener.Point.First)) {
                ((FirstAnalysisListener) listener).parseFirst(span, segmentObject);
            }
        });
    }

    private void notifySegmentListener(SegmentObject segmentObject) {
        analysisListeners.forEach(listener -> {
            if (listener.containsPoint(AnalysisListener.Point.Segment)) {
                ((SegmentListener) listener).parseSegment(segmentObject);
            }
        });
    }

    private void createSpanListeners() {
        listenerManager.getSpanListenerFactories()
                       .forEach(
                           spanListenerFactory -> analysisListeners.add(
                               spanListenerFactory.create(moduleManager, config)));
    }
}
