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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.SneakyThrows;
import org.apache.skywalking.apm.network.logging.v3.LogData;

import org.apache.skywalking.oap.log.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.LALOutputBuilder;
import org.apache.skywalking.oap.server.core.source.LogBuilder;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RecordSinkListener forwards LAL output to the persistence layer.
 *
 * <p>All LAL rules produce an {@link LALOutputBuilder} in the {@link ExecutionContext}.
 * This listener calls {@code init()} to populate standard fields from LogData,
 * then {@code complete()} to dispatch the final source(s).
 */
public class RecordSinkListener implements LogSinkListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecordSinkListener.class);
    private final SourceReceiver sourceReceiver;
    private final NamingControl namingControl;
    private final List<String> searchableTagKeys;

    private LALOutputBuilder builder;

    RecordSinkListener(final SourceReceiver sourceReceiver,
                       final NamingControl namingControl,
                       final List<String> searchableTagKeys) {
        this.sourceReceiver = sourceReceiver;
        this.namingControl = namingControl;
        this.searchableTagKeys = searchableTagKeys;
    }

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

    @Override
    @SneakyThrows
    public LogSinkListener parse(final LogData.Builder logData,
                                     final Optional<Object> extraLog) {
        return this;
    }

    @Override
    @SneakyThrows
    public LogSinkListener parse(final LogData.Builder logData,
                                 final Optional<Object> extraLog,
                                 final ExecutionContext ctx) {
        if (ctx == null || !(ctx.output() instanceof LALOutputBuilder)) {
            return this;
        }
        builder = ctx.outputAsBuilder();
        if (builder instanceof LogBuilder) {
            ((LogBuilder) builder).setSearchableTagKeys(searchableTagKeys);
        }
        // Pass the input data matching the declared inputType:
        // extraLog (e.g., HTTPAccessLogEntry) when present, otherwise LogData.
        builder.init(logData.build(), extraLog, namingControl);
        return this;
    }

    public LALOutputBuilder getBuilder() {
        return builder;
    }

    public static class Factory implements LogSinkListenerFactory {
        private final SourceReceiver sourceReceiver;
        private final NamingControl namingControl;
        private final List<String> searchableTagKeys;

        public Factory(ModuleManager moduleManager, LogAnalyzerModuleConfig moduleConfig) {
            this.sourceReceiver = moduleManager.find(CoreModule.NAME)
                                               .provider()
                                               .getService(SourceReceiver.class);
            this.namingControl = moduleManager.find(CoreModule.NAME)
                                              .provider()
                                              .getService(NamingControl.class);
            ConfigService configService = moduleManager.find(CoreModule.NAME)
                                                       .provider()
                                                       .getService(ConfigService.class);
            this.searchableTagKeys = Arrays.asList(configService.getSearchableLogsTags().split(Const.COMMA));
        }

        @Override
        public RecordSinkListener create() {
            return new RecordSinkListener(sourceReceiver, namingControl, searchableTagKeys);
        }
    }
}
