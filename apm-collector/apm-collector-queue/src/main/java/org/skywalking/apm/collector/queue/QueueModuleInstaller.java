package org.skywalking.apm.collector.queue;

import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.config.ConfigException;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.core.framework.Context;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.module.SingleModuleInstaller;
import org.skywalking.apm.collector.core.server.ServerException;
import org.skywalking.apm.collector.queue.datacarrier.DataCarrierQueueCreator;

/**
 * @author pengys5
 */
public class QueueModuleInstaller extends SingleModuleInstaller {

    @Override public String groupName() {
        return QueueModuleGroupDefine.GROUP_NAME;
    }

    @Override public Context moduleContext() {
        return new QueueModuleContext(groupName());
    }

    @Override public void install() throws ClientException, DefineException, ConfigException, ServerException {
        super.install();
        ((QueueModuleContext)CollectorContextHelper.INSTANCE.getContext(groupName())).setQueueCreator(new DataCarrierQueueCreator());
    }
}
