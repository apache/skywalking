/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.core.module;

import java.util.List;
import java.util.Map;
import org.skywalking.apm.collector.core.CollectorException;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.config.ConfigException;
import org.skywalking.apm.collector.core.framework.Context;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.server.ServerException;
import org.skywalking.apm.collector.core.server.ServerHolder;

/**
 * @author peng-yongsheng
 */
public interface ModuleInstaller {

    List<String> dependenceModules();

    void injectServerHolder(ServerHolder serverHolder);

    String groupName();

    Context moduleContext();

    void injectConfiguration(Map<String, Map> moduleConfig, Map<String, ModuleDefine> moduleDefineMap);

    void preInstall() throws DefineException, ConfigException, ServerException;

    void install() throws ClientException, DefineException, ConfigException, ServerException;

    void afterInstall() throws CollectorException;
}
