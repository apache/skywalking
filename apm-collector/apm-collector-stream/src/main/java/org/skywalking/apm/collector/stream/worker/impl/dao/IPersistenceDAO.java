package org.skywalking.apm.collector.stream.worker.impl.dao;

import org.skywalking.apm.collector.stream.worker.impl.data.Data;
import org.skywalking.apm.collector.stream.worker.impl.data.DataDefine;

/**
 * @author pengys5
 */
public interface IPersistenceDAO<I, U> {
    Data get(String id, DataDefine dataDefine);

    I prepareBatchInsert(Data data);

    U prepareBatchUpdate(Data data);
}
