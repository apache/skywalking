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

package org.apache.skywalking.oap.server.core.alarm.provider;

import org.apache.skywalking.oap.server.configuration.api.ConfigChangeWatcher;
import org.apache.skywalking.oap.server.core.alarm.AlarmModule;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import static org.apache.skywalking.oap.server.core.alarm.provider.Rules.EMPTY;

/**
 * @author kezhenxu94
 */
public class AlarmRulesWatcher extends ConfigChangeWatcher {
    private final Consumer<Rules> reload;

    private volatile String raw;

    AlarmRulesWatcher(final Rules initRules, AlarmModuleProvider provider, Consumer<Rules> reload) {
        super(AlarmModule.NAME, provider, "alarm-settings");
        this.reload = reload;
        this.reload.accept(initRules);
    }

    @Override
    public void notify(ConfigChangeEvent value) {
        if (EventType.DELETE.equals(value.getEventType())) {
            raw = "";
            this.reload.accept(EMPTY);
        } else {
            raw = value.getNewValue();
            final String config = value.getNewValue();
            final InputStream configStream = new ByteArrayInputStream(config.getBytes(StandardCharsets.UTF_8));
            final RulesReader reader = new RulesReader(configStream);
            final Rules rules = reader.readRules();
            this.reload.accept(rules);
        }
    }

    @Override
    public String value() {
        return raw;
    }
}
