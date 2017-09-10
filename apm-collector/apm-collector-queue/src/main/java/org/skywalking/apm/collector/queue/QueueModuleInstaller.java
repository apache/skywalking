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
 * @author pengys5
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
