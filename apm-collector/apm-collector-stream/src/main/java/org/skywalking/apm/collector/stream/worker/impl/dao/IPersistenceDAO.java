package org.skywalking.apm.collector.stream.worker.impl.dao;

import org.skywalking.apm.collector.core.stream.Data;
import org.skywalking.apm.collector.storage.define.DataDefine;

/**
 * @author pengys5
 */
public interface IPersistenceDAO<I, U> {
    Data get(String id, DataDefine dataDefine);

    I prepareBatchInsert(Data data);

    U prepareBatchUpdate(Data data);
}
