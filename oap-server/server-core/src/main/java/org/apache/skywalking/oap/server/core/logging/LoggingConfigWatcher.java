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

package org.apache.skywalking.oap.server.core.logging;

import com.google.common.base.Strings;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.skywalking.oap.server.configuration.api.ConfigChangeWatcher;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.logging.log4j.OapConfiguration;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;

/**
 * LoggingConfigWatcher watches the change of logging configuration.
 * Once got the change content, it would apply them to the current logger context.
 */
@Slf4j
public class LoggingConfigWatcher extends ConfigChangeWatcher {
    private final LoggerContext ctx;
    private final OapConfiguration originConfiguration;
    private String content;

    public LoggingConfigWatcher(final ModuleProvider provider) {
        super(CoreModule.NAME, provider, "log4j-xml");
        this.ctx = (LoggerContext) LogManager.getContext(false);
        this.originConfiguration = (OapConfiguration) ctx.getConfiguration();
    }

    @Override
    public void notify(final ConfigChangeEvent value) {
        if (EventType.DELETE.equals(value.getEventType())) {
            this.content = "";
        } else {
            this.content = value.getNewValue();
        }
        try {
            boolean applied = updateConfig();
            if (log.isDebugEnabled() && applied) {
                log.debug("applied {} B data to logging configuration", this.content.length());
            }
        } catch (Throwable t) {
            log.error("failed to apply configuration to log4j", t);
        }
    }

    @Override
    public String value() {
        return this.content;
    }

    private boolean updateConfig() {
        if (Strings.isNullOrEmpty(content)) {
            if (ctx.getConfiguration().equals(originConfiguration)) {
                ctx.onChange(originConfiguration);
                return true;
            }
            return false;
        }
        OapConfiguration oc;
        try {
            oc = new OapConfiguration(ctx, new ConfigurationSource(new ByteArrayInputStream(content.getBytes())));
        } catch (IOException e) {
            throw new RuntimeException("failed to parse string from configuration center", e);
        }
        oc.initialize();
        ctx.onChange(oc);
        return true;
    }
}
