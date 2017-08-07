package org.skywalking.apm.collector.agentstream.worker.node.component.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.skywalking.apm.collector.agentstream.worker.node.component.define.NodeComponentTable;
import org.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.skywalking.apm.collector.stream.worker.impl.data.Data;

/**
 * @author pengys5
 */
public class NodeComponentEsDAO extends EsDAO implements INodeComponentDAO {

    @Override public List<?> prepareBatch(Map<String, Data> dataMap) {
        List<IndexRequestBuilder> indexRequestBuilders = new ArrayList<>();
        dataMap.forEach((id, data) -> {
            Map<String, Object> source = new HashMap();
            source.put(NodeComponentTable.COLUMN_APPLICATION_ID, data.getDataInteger(0));
            source.put(NodeComponentTable.COLUMN_COMPONENT_NAME, data.getDataString(1));
            source.put(NodeComponentTable.COLUMN_COMPONENT_ID, data.getDataInteger(1));

            IndexRequestBuilder builder = getClient().prepareIndex(NodeComponentTable.TABLE, id).setSource(source);
            indexRequestBuilders.add(builder);
        });
        return indexRequestBuilders;
    }

    public int getComponentId(int applicationId, String componentName) {
        ElasticSearchClient client = getClient();

        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(NodeComponentTable.TABLE);
        searchRequestBuilder.setTypes("type");
        searchRequestBuilder.setSearchType(SearchType.QUERY_THEN_FETCH);
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.must(QueryBuilders.termQuery(NodeComponentTable.COLUMN_APPLICATION_ID, applicationId));
        boolQueryBuilder.must(QueryBuilders.termQuery(NodeComponentTable.COLUMN_COMPONENT_NAME, componentName));
        searchRequestBuilder.setQuery(boolQueryBuilder);
        searchRequestBuilder.setSize(1);

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        if (searchResponse.getHits().totalHits > 0) {
            SearchHit searchHit = searchResponse.getHits().iterator().next();
            int componentId = (int)searchHit.getSource().get(NodeComponentTable.COLUMN_COMPONENT_ID);
            return componentId;
        }
        return 0;
    }
}
