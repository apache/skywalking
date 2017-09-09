package org.skywalking.apm.collector.agentjvm.worker.cpu.dao;

import java.util.HashMap;
import java.util.Map;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.skywalking.apm.collector.core.stream.Data;
import org.skywalking.apm.collector.storage.define.DataDefine;
import org.skywalking.apm.collector.storage.define.jvm.CpuMetricTable;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.skywalking.apm.collector.stream.worker.impl.dao.IPersistenceDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class CpuMetricEsDAO extends EsDAO implements ICpuMetricDAO, IPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder> {

    private final Logger logger = LoggerFactory.getLogger(CpuMetricEsDAO.class);

    @Override public Data get(String id, DataDefine dataDefine) {
        return null;
    }

    @Override public IndexRequestBuilder prepareBatchInsert(Data data) {
        Map<String, Object> source = new HashMap<>();
        source.put(CpuMetricTable.COLUMN_INSTANCE_ID, data.getDataInteger(0));
        source.put(CpuMetricTable.COLUMN_USAGE_PERCENT, data.getDataDouble(0));
        source.put(CpuMetricTable.COLUMN_TIME_BUCKET, data.getDataLong(0));

        logger.debug("prepare cpu metric batch insert, id: {}", data.getDataString(0));
        return getClient().prepareIndex(CpuMetricTable.TABLE, data.getDataString(0)).setSource(source);
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(Data data) {
        return null;
    }
}
