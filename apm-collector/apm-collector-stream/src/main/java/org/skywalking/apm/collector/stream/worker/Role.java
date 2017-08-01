package org.skywalking.apm.collector.stream.worker;

import org.skywalking.apm.collector.stream.worker.impl.data.DataDefine;
import org.skywalking.apm.collector.stream.worker.selector.WorkerSelector;

/**
 * @author pengys5
 */
public interface Role {

    String roleName();

    WorkerSelector workerSelector();

    DataDefine dataDefine();
}
