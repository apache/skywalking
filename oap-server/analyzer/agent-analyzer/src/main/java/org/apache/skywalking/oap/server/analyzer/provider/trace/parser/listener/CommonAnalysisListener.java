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

import org.apache.skywalking.apm.network.language.agent.v3.SpanLayer;
import org.apache.skywalking.oap.server.core.analysis.Layer;

/**
 * The common logic for specific listeners.
 */
abstract class CommonAnalysisListener {
    /**
     * Identify the layer of span's service/instance owner. Such as  ${@link Layer#FAAS} and ${@link Layer#GENERAL}.
     */
    protected Layer identifyServiceLayer(SpanLayer spanLayer) {
        if (SpanLayer.FAAS.equals(spanLayer)) {
            // function as a Service
            return Layer.FAAS;
        } else {
            return Layer.GENERAL;
        }
    }
}
