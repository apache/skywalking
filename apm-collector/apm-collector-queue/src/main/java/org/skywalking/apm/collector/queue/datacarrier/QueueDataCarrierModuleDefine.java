package org.skywalking.apm.collector.queue.datacarrier;

import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.queue.QueueModuleContext;
import org.skywalking.apm.collector.queue.QueueModuleDefine;
import org.skywalking.apm.collector.queue.QueueModuleGroupDefine;

/**
 * @author pengys5
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

    @Override protected void initializeOtherContext() {
        ((QueueModuleContext)CollectorContextHelper.INSTANCE.getContext(group())).setQueueCreator(new DataCarrierQueueCreator());
    }
}
