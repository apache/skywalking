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
 */

package org.apache.skywalking.oap.analyzer.ios.listener;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.ServiceMeta;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.core.trace.OTLPSpanReader;
import org.apache.skywalking.oap.server.core.trace.SpanListener;
import org.apache.skywalking.oap.server.core.trace.SpanListenerResult;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * Detects iOS/iPadOS spans from the {@code os.name} resource attribute and registers
 * the service with {@link Layer#IOS}.
 *
 * <p>Implements phase 1 ({@link #onOTLPSpan}) only — HTTP trace spans are persisted
 * normally via the Zipkin pipeline; this listener just ensures the service layer is set.
 */
@Slf4j
public class IOSLayerSpanListener implements SpanListener {
    private SourceReceiver sourceReceiver;
    private NamingControl namingControl;

    @Override
    public String[] requiredModules() {
        return new String[] {CoreModule.NAME};
    }

    @Override
    public void init(final ModuleManager moduleManager) {
        sourceReceiver = moduleManager.find(CoreModule.NAME)
                                      .provider()
                                      .getService(SourceReceiver.class);
        namingControl = moduleManager.find(CoreModule.NAME)
                                     .provider()
                                     .getService(NamingControl.class);
    }

    @Override
    public SpanListenerResult onOTLPSpan(final OTLPSpanReader span,
                                         final Map<String, String> resourceAttributes,
                                         final String scopeName,
                                         final String scopeVersion) {
        final String osName = resourceAttributes.get("os.name");
        if (!"iOS".equals(osName) && !"iPadOS".equals(osName)) {
            return SpanListenerResult.CONTINUE;
        }

        final String serviceName = resourceAttributes.get("service.name");
        if (serviceName == null || serviceName.isEmpty()) {
            return SpanListenerResult.CONTINUE;
        }

        // Register the service with IOS layer
        final ServiceMeta serviceMeta = new ServiceMeta();
        serviceMeta.setName(namingControl.formatServiceName(serviceName));
        serviceMeta.setLayer(Layer.IOS);
        serviceMeta.setTimeBucket(TimeBucket.getMinuteTimeBucket(
            span.endTimeNanos() / 1_000_000));
        sourceReceiver.receive(serviceMeta);

        return SpanListenerResult.CONTINUE;
    }
}
