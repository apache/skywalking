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
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.framework.Define;
import org.skywalking.apm.collector.core.framework.Handler;
import org.skywalking.apm.collector.core.server.Server;

/**
 * @author peng-yongsheng
 */
public abstract class ModuleDefine implements Define {

    protected abstract String group();

    public abstract boolean defaultModule();

    protected abstract ModuleConfigParser configParser();

    protected abstract Client createClient();

    protected abstract Server server();

    public abstract List<Handler> handlerList();

    protected abstract ModuleRegistration registration();

    protected abstract void initializeOtherContext();
}
