package org.skywalking.apm.collector.queue.disruptor;

import java.util.Map;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.module.ModuleGroup;
import org.skywalking.apm.collector.core.queue.QueueModuleContext;
import org.skywalking.apm.collector.core.queue.QueueModuleDefine;

/**
 * @author pengys5
 */
public class QueueDisruptorModuleDefine extends QueueModuleDefine {

    @Override protected ModuleGroup group() {
        return ModuleGroup.Queue;
    }

    @Override public String name() {
        return "disruptor";
    }

    @Override public boolean defaultModule() {
        return true;
    }

    @Override public final void initialize(Map config) throws DefineException, ClientException {
        QueueModuleContext.CREATOR = new DisruptorCreator();
    }
}
