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

import com.google.common.base.Strings;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.skywalking.oap.server.configuration.api.ConfigurationModule;
import org.apache.skywalking.oap.server.configuration.api.DynamicConfigurationService;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.logging.module.LoggingModule;
import org.apache.skywalking.oap.server.logging.provider.log4j.OapConfiguration;

public class LoggingProvider extends ModuleProvider {

    private LoggingConfigWatcher configWatcher;

    @Override
    public String name() {
        return "default";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return LoggingModule.class;
    }

    @Override
    public ModuleConfig createConfigBeanIfAbsent() {
        return new LoggingConfig();
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final OapConfiguration originConfiguration = (OapConfiguration) ctx.getConfiguration();
        configWatcher = new LoggingConfigWatcher(this, content -> {
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
        });
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {
        DynamicConfigurationService dynamicConfigurationService = getManager().find(ConfigurationModule.NAME)
                                                                              .provider()
                                                                              .getService(
                                                                                  DynamicConfigurationService.class);
        dynamicConfigurationService.registerConfigChangeWatcher(configWatcher);

    }

    @Override
    public String[] requiredModules() {
        return new String[]{ConfigurationModule.NAME};
    }
}
