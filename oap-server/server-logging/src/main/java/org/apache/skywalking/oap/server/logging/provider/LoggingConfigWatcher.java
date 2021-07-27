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

package org.apache.skywalking.oap.server.logging.provider;

import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.configuration.api.ConfigChangeWatcher;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.logging.module.LoggingModule;

@Slf4j
public class LoggingConfigWatcher extends ConfigChangeWatcher {
    private final Function<String, Boolean> configureCaller;
    private String content;

    public LoggingConfigWatcher(final ModuleProvider provider, final Function<String, Boolean> configureCaller) {
        super(LoggingModule.NAME, provider, "log4j-xml");
        this.configureCaller = configureCaller;
    }

    @Override
    public void notify(final ConfigChangeEvent value) {
        if (EventType.DELETE.equals(value.getEventType())) {
            this.content = "";
        } else {
            this.content = value.getNewValue();
        }
        try {
            Boolean applied = this.configureCaller.apply(this.content);
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
}
