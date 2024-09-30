package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query;

import com.google.common.collect.Lists;
import org.apache.skywalking.library.elasticsearch.requests.search.BoolQueryBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHit;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.storage.AsyncProfilerTaskRecord;
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.storage.JfrProfilingDataRecord;
import org.apache.skywalking.oap.server.core.storage.profiling.asyncprofiler.IJfrDataQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.ElasticSearchConverter;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;

import java.util.List;
import java.util.Map;

public class JfrDataQueryEsDAO extends EsDAO implements IJfrDataQueryDAO {
    public JfrDataQueryEsDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public List<JfrProfilingDataRecord> getById(String taskId, List<String> instanceIds, String eventType) {
        final String index = IndexController.LogicIndicesRegister.getPhysicalTableName(JfrProfilingDataRecord.INDEX_NAME);
        final BoolQueryBuilder query = Query.bool();
        if (IndexController.LogicIndicesRegister.isMergedTable(AsyncProfilerTaskRecord.INDEX_NAME)) {
            query.must(Query.term(IndexController.LogicIndicesRegister.RECORD_TABLE_NAME, JfrProfilingDataRecord.INDEX_NAME));
        }
        query.must(Query.term(JfrProfilingDataRecord.TASK_ID, taskId));
        query.must(Query.term(JfrProfilingDataRecord.EVENT_TYPE, eventType));
        if (CollectionUtils.isNotEmpty(instanceIds)) {
            query.must(Query.terms(JfrProfilingDataRecord.INSTANCE_ID, instanceIds));
        }
        final SearchBuilder search = Search.builder()
                .query(query);

        final SearchResponse response = getClient().search(index, search.build());
        List<JfrProfilingDataRecord> dataRecords = Lists.newArrayList();
        for (SearchHit searchHit : response.getHits().getHits()) {
            dataRecords.add(parseData(searchHit));
        }
        return dataRecords;
    }

    private JfrProfilingDataRecord parseData(SearchHit data) {
        final Map<String, Object> sourceAsMap = data.getSource();
        final JfrProfilingDataRecord.Builder builder = new JfrProfilingDataRecord.Builder();
        return builder.storage2Entity(new ElasticSearchConverter.ToEntity(JfrProfilingDataRecord.INDEX_NAME, sourceAsMap));
    }
}
