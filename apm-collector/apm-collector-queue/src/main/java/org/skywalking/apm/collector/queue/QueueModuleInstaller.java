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
import org.skywalking.apm.collector.core.CollectorException;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.config.ConfigException;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.core.framework.Context;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.framework.UnexpectedException;
import org.skywalking.apm.collector.core.module.SingleModuleInstaller;
import org.skywalking.apm.collector.core.server.ServerException;
import org.skywalking.apm.collector.queue.datacarrier.DataCarrierQueueCreator;
import org.skywalking.apm.collector.queue.datacarrier.QueueDataCarrierModuleDefine;
import org.skywalking.apm.collector.queue.disruptor.DisruptorQueueCreator;
import org.skywalking.apm.collector.queue.disruptor.QueueDisruptorModuleDefine;

/**
 * @author peng-yongsheng
 */
public class QueueModuleInstaller extends SingleModuleInstaller {

    @Override public String groupName() {
        return QueueModuleGroupDefine.GROUP_NAME;
    }

    @Override public Context moduleContext() {
        return new QueueModuleContext(groupName());
    }

    @Override public List<String> dependenceModules() {
        return null;
    }

    @Override public void install() throws ClientException, DefineException, ConfigException, ServerException {
        super.install();
        if (getModuleDefine() instanceof QueueDataCarrierModuleDefine) {
            ((QueueModuleContext)CollectorContextHelper.INSTANCE.getContext(groupName())).setQueueCreator(new DataCarrierQueueCreator());
        } else if (getModuleDefine() instanceof QueueDisruptorModuleDefine) {
            ((QueueModuleContext)CollectorContextHelper.INSTANCE.getContext(groupName())).setQueueCreator(new DisruptorQueueCreator());
        } else {
            throw new UnexpectedException("");
        }
    }

    @Override public void onAfterInstall() throws CollectorException {
    }
}
