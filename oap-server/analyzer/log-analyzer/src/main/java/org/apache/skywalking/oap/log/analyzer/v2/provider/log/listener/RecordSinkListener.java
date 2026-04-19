/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.oap.log.analyzer.v2.provider.log.listener;

import lombok.SneakyThrows;

import org.apache.skywalking.oap.log.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.source.LALOutputBuilder;
import org.apache.skywalking.oap.server.core.source.LogMetadata;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RecordSinkListener forwards LAL output to the persistence layer.
 *
 * <p>All LAL rules produce an {@link LALOutputBuilder} in the {@link ExecutionContext}.
 * This listener calls {@code init()} to populate standard fields from metadata,
 * then {@code complete()} to dispatch the final source(s).
 */
public class RecordSinkListener implements LogSinkListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecordSinkListener.class);
    private final SourceReceiver sourceReceiver;
    private final ModuleManager moduleManager;

    private LALOutputBuilder builder;

    RecordSinkListener(final SourceReceiver sourceReceiver,
                       final ModuleManager moduleManager) {
        this.sourceReceiver = sourceReceiver;
        this.moduleManager = moduleManager;
    }

    /**
     * Dispatch the finalized output. {@link LALOutputBuilder#complete(SourceReceiver)}
     * is polymorphic: the default {@code LogBuilder} emits a {@code Log} source; custom
     * builders registered by receivers (e.g., {@code EnvoyAccessLogBuilder},
     * {@code DatabaseSlowStatementBuilder}, {@code SampledTraceBuilder}) emit their own
     * source types. {@code builder} is {@code null} only when {@link #parse} skipped —
     * in which case there's nothing to persist.
     */
    @Override
    public void build() {
        if (builder == null) {
            return;
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("RecordSinkListener invoking builder.complete() on {}",
                builder.getClass().getSimpleName());
        }
        builder.complete(sourceReceiver);
    }

    /**
     * Pick up the output object produced by the LAL extractor and prepare it for dispatch.
     *
     * <p>The LAL codegen always creates an output at the start of {@code execute()}
     * via {@code ctx.setOutput(new OutputType())}. {@code OutputType} is resolved per rule
     * from (in order): the YAML rule's {@code outputType} field, an
     * {@code LALSourceTypeProvider} SPI contribution for the layer, or the default
     * {@code LogBuilder}. All valid output types implement {@link LALOutputBuilder}, so
     * the {@code instanceof} check below is a defensive null-guard, not a selector.
     * Calling {@code init()} populates standard fields from metadata (service, layer,
     * timestamp, trace context) only if the extractor left them blank.
     */
    @Override
    @SneakyThrows
    public LogSinkListener parse(final LogMetadata metadata,
                                 final Object input,
                                 final ExecutionContext ctx) {
        if (ctx == null || !(ctx.output() instanceof LALOutputBuilder)) {
            return this;
        }
        builder = ctx.outputAsBuilder();
        builder.init(metadata, input, moduleManager);
        return this;
    }

    public static class Factory implements LogSinkListenerFactory {
        private final SourceReceiver sourceReceiver;
        private final ModuleManager moduleManager;

        public Factory(ModuleManager moduleManager, LogAnalyzerModuleConfig moduleConfig) {
            this.sourceReceiver = moduleManager.find(CoreModule.NAME)
                                               .provider()
                                               .getService(SourceReceiver.class);
            this.moduleManager = moduleManager;
        }

        @Override
        public RecordSinkListener create() {
            return new RecordSinkListener(sourceReceiver, moduleManager);
        }
    }
}
