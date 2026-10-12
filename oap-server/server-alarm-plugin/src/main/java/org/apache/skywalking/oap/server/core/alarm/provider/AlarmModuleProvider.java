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

import java.io.FileNotFoundException;
import java.io.Reader;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import org.apache.skywalking.oap.server.configuration.api.ConfigurationModule;
import org.apache.skywalking.oap.server.configuration.api.DynamicConfigurationService;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.alarm.AlarmKernelService;
import org.apache.skywalking.oap.server.core.alarm.AlarmModule;
import org.apache.skywalking.oap.server.core.alarm.AlarmRulesWatcherService;
import org.apache.skywalking.oap.server.core.alarm.AlarmStandardPersistence;
import org.apache.skywalking.oap.server.core.alarm.AlarmStatusWatcherService;
import org.apache.skywalking.oap.server.core.alarm.HttpAlarmCallback;
import org.apache.skywalking.oap.server.core.alarm.MetricsNotify;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;

public class AlarmModuleProvider extends ModuleProvider {
    private static final long MAX_HTTP_TIMEOUT_SECONDS = TimeUnit.NANOSECONDS.toSeconds(Long.MAX_VALUE);

    private NotifyHandler notifyHandler;
    @Getter
    private AlarmRulesWatcher alarmRulesWatcher;
    private AlarmModuleConfig moduleConfig = new AlarmModuleConfig();

    @Override
    public String name() {
        return "default";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return AlarmModule.class;
    }

    @Override
    public ConfigCreator<? extends ModuleConfig> newConfigCreator() {
        return new ConfigCreator<AlarmModuleConfig>() {
            @Override
            public Class<AlarmModuleConfig> type() {
                return AlarmModuleConfig.class;
            }

            @Override
            public void onInitialized(final AlarmModuleConfig initialized) {
                moduleConfig = initialized;
            }
        };
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        alarmRulesWatcher = new AlarmRulesWatcher(new Rules(), this, getManager());
        final Duration httpTimeout = httpTimeout(moduleConfig.getHttpTimeout());
        notifyHandler = new NotifyHandler(
            alarmRulesWatcher, getManager(), HttpAlarmCallback.newHttpClient(httpTimeout), httpTimeout);
        this.registerServiceImplementation(MetricsNotify.class, notifyHandler);
        this.registerServiceImplementation(AlarmRulesWatcherService.class, alarmRulesWatcher);
        this.registerServiceImplementation(AlarmStatusWatcherService.class, new AlarmStatusWatcher(getManager()));
        this.registerServiceImplementation(AlarmKernelService.class,
            new AlarmKernel(alarmRulesWatcher));
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        DynamicConfigurationService dynamicConfigurationService = getManager().find(ConfigurationModule.NAME)
                                                                              .provider()
                                                                              .getService(
                                                                                  DynamicConfigurationService.class);
        dynamicConfigurationService.registerConfigChangeWatcher(alarmRulesWatcher);
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {
        Reader applicationReader;
        try {
            applicationReader = ResourceUtils.read("alarm-settings.yml");
        } catch (FileNotFoundException e) {
            throw new ModuleStartException("can't load alarm-settings.yml", e);
        }
        RulesReader reader = new RulesReader(applicationReader, getManager());
        Rules rules = reader.readRules();
        alarmRulesWatcher.initConfig(rules);
        notifyHandler.init(new AlarmStandardPersistence(getManager()));
    }

    /**
     * The cap keeps the JDK's own timer arithmetic from overflowing: {@code Long.MAX_VALUE} seconds is accepted by
     * {@code HttpRequest.Builder#timeout} and then fails every send with an {@link ArithmeticException}.
     */
    static Duration httpTimeout(final long seconds) {
        if (seconds <= 0) {
            return Duration.ofSeconds(AlarmModuleConfig.DEFAULT_HTTP_TIMEOUT_SECONDS);
        }
        return Duration.ofSeconds(Math.min(seconds, MAX_HTTP_TIMEOUT_SECONDS));
    }

    @Override
    public String[] requiredModules() {
        return new String[] {
            CoreModule.NAME,
            ConfigurationModule.NAME
        };
    }
}
