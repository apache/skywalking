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

package org.apache.skywalking.oap.server.analyzer.event;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.event.v3.Event;
import org.apache.skywalking.oap.server.analyzer.event.listener.EventAnalyzerListener;
import org.apache.skywalking.oap.server.analyzer.event.listener.EventAnalyzerListenerFactoryManager;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * Analyze the collected event data, is the entry point for event analysis.
 */
@Slf4j
@RequiredArgsConstructor
public class EventAnalyzer {
    private final ModuleManager moduleManager;

    private final EventAnalyzerListenerFactoryManager factoryManager;

    private final List<EventAnalyzerListener> listeners = new ArrayList<>();

    public void analyze(final Event builder) {
        createListeners();
        notifyListener(builder);
        notifyListenerToBuild();
    }

    private void notifyListener(final Event event) {
        listeners.forEach(listener -> listener.parse(event));
    }

    private void notifyListenerToBuild() {
        listeners.forEach(EventAnalyzerListener::build);
    }

    private void createListeners() {
        factoryManager.factories()
                      .forEach(factory -> listeners.add(factory.create(moduleManager)));
    }
}
