package org.skywalking.apm.collector.queue.disruptor;

import java.util.Map;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.queue.QueueModuleContext;
import org.skywalking.apm.collector.queue.QueueModuleDefine;
import org.skywalking.apm.collector.queue.QueueModuleGroupDefine;

/**
 * @author pengys5
 */
public class QueueDisruptorModuleDefine extends QueueModuleDefine {

    @Override protected String group() {
        return QueueModuleGroupDefine.GROUP_NAME;
    }

    @Override public String name() {
        return "disruptor";
    }

    @Override public boolean defaultModule() {
        return true;
    }

    @Override public final void initialize(Map config) throws DefineException, ClientException {
        ((QueueModuleContext)CollectorContextHelper.INSTANCE.getContext(group())).setQueueCreator(new DisruptorQueueCreator());
    }
}
