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

package org.skywalking.apm.collector.queue;

import java.util.List;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.framework.Handler;
import org.skywalking.apm.collector.core.module.ModuleDefine;
import org.skywalking.apm.collector.core.module.ModuleRegistration;
import org.skywalking.apm.collector.core.server.Server;

/**
 * @author peng-yongsheng
 */
public abstract class QueueModuleDefine extends ModuleDefine {

    @Override protected Client createClient() {
        return null;
    }

    @Override protected final ModuleRegistration registration() {
        throw new UnsupportedOperationException("");
    }

    @Override protected final Server server() {
        return null;
    }

    @Override public final List<Handler> handlerList() {
        return null;
    }
}
