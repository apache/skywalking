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

package org.apache.skywalking.oap.query.debug;

import com.linecorp.armeria.common.HttpMethod;
import java.util.Collections;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.server.HTTPHandlerRegister;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;

public class StatusQueryProvider extends ModuleProvider {
    public static final String NAME = "default";

    private StatusQueryConfig config;

    public String name() {
        return NAME;
    }

    public Class<? extends ModuleDefine> module() {
        return StatusQueryModule.class;
    }

    public ConfigCreator<? extends ModuleConfig> newConfigCreator() {
        return new ConfigCreator<StatusQueryConfig>() {
            @Override
            public Class<StatusQueryConfig> type() {
                return StatusQueryConfig.class;
            }

            @Override
            public void onInitialized(final StatusQueryConfig initialized) {
                config = initialized;
            }
        };
    }

    public void prepare() throws ServiceNotProvidedException {

    }

    public void start() throws ServiceNotProvidedException {
        HTTPHandlerRegister service = getManager().find(CoreModule.NAME)
                                                  .provider()
                                                  .getService(HTTPHandlerRegister.class);
        service.addHandler(
            new DebuggingHTTPHandler(getManager(), config),
            Collections.singletonList(HttpMethod.GET)
        );
        service.addHandler(
            new TTLConfigQueryHandler(getManager()),
            Collections.singletonList(HttpMethod.GET)
        );
        service.addHandler(
            new ClusterStatusQueryHandler(getManager()),
            Collections.singletonList(HttpMethod.GET)
        );
    }

    public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {

    }

    public String[] requiredModules() {
        return new String[] {CoreModule.NAME};
    }
}
