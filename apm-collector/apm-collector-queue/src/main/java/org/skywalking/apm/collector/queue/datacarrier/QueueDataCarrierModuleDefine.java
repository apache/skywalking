package org.skywalking.apm.collector.queue.datacarrier;

import java.util.Map;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.server.ServerHolder;
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
        return true;
    }

    @Override
    public final void initialize(Map config, ServerHolder serverHolder) throws DefineException, ClientException {
        ((QueueModuleContext)CollectorContextHelper.INSTANCE.getContext(group())).setQueueCreator(new DataCarrierQueueCreator());
    }
}
