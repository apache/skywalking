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

package org.skywalking.apm.collector.agentstream;

import java.util.List;
import org.skywalking.apm.collector.agentstream.worker.storage.PersistenceTimer;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.config.ConfigException;
import org.skywalking.apm.collector.core.framework.Context;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.module.MultipleModuleInstaller;
import org.skywalking.apm.collector.core.server.ServerException;

/**
 * @author peng-yongsheng
 */
public class AgentStreamModuleInstaller extends MultipleModuleInstaller {

    @Override public String groupName() {
        return AgentStreamModuleGroupDefine.GROUP_NAME;
    }

    @Override public Context moduleContext() {
        return new AgentStreamModuleContext(groupName());
    }

    @Override public List<String> dependenceModules() {
        return null;
    }

    @Override public void install() throws DefineException, ConfigException, ServerException, ClientException {
        super.install();
        new PersistenceTimer().start();
    }
}
