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

package org.skywalking.apm.collector.queue.datacarrier;

import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.queue.QueueModuleContext;
import org.skywalking.apm.collector.queue.QueueModuleDefine;
import org.skywalking.apm.collector.queue.QueueModuleGroupDefine;

/**
 * @author peng-yongsheng
 */
public class QueueDataCarrierModuleDefine extends QueueModuleDefine {

    @Override protected String group() {
        return QueueModuleGroupDefine.GROUP_NAME;
    }

    @Override public String name() {
        return "data_carrier";
    }

    @Override public boolean defaultModule() {
        return false;
    }

    @Override protected ModuleConfigParser configParser() {
        return new DataCarrierQueueConfigParser();
    }

    @Override protected void initializeOtherContext() {
        ((QueueModuleContext)CollectorContextHelper.INSTANCE.getContext(group())).setQueueCreator(new DataCarrierQueueCreator());
    }
}
