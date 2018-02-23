package org.apache.skywalking.apm.collector.storage.es.http;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.apache.skywalking.apm.collector.storage.table.register.NetworkAddressTable;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.searchbox.client.JestClient;
import io.searchbox.client.JestClientFactory;
import io.searchbox.client.JestResult;
import io.searchbox.client.config.HttpClientConfig;
import io.searchbox.core.DeleteByQuery;
import io.searchbox.core.DocumentResult;
import io.searchbox.core.Get;
import io.searchbox.core.MultiGet;
import io.searchbox.core.Search;
import io.searchbox.core.SearchResult;

public class JestClient1 {
        public static void main(String[] args) {
            JestClientFactory factory = new JestClientFactory();
            factory.setHttpClientConfig(new HttpClientConfig
                                   .Builder("http://10.126.126.75:29200")
                                   .multiThreaded(true)
                                   .discoveryFrequency(1, TimeUnit.MINUTES)
                                   .discoveryEnabled(true)
                                   .defaultCredentials("admin", "admin")
                       //Per default this implementation will create no more than 2 concurrent connections per given route
                       .defaultMaxTotalConnectionPerRoute(1)
                       // and no more 20 connections in total
                       .maxTotalConnection(10)
                                   .build());
            
            JestClient client = factory.getObject();
            
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(QueryBuilders.matchQuery("sceneid", "53"));

            Search search = new Search.Builder(searchSourceBuilder.toString())
                                            // multiple index or types can be added.
                                            .addIndex("strategy-53-20171001")
                                            .addType("strategy")
                                            .build();
        
            try {
                SearchResult result = client.execute(search);
               JsonArray array =  result.getJsonObject().getAsJsonObject("hits").getAsJsonArray("hits");
               for(Object x : array){
                   JsonObject a = (JsonObject) x ; 
//                   System.out.println(a.getAsJsonObject("_source").get("NetworkAddressTable.COLUMN_ADDRESS_ID").getAsInt());
               }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            
            String index = "strategy-53-20171001";
            String type = "strategy";
            Get get = new Get.Builder("strategy-53-20171001", "AV7XWH8z2L7oY3_0qZQY").build();
            try {
                DocumentResult result = client.execute(get);
                JsonObject json = result.getSourceAsObject(JsonObject.class);
                System.out.println(json);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            
            MultiGet mg = new MultiGet.Builder.ById(index, type).addId("AV7XWH8z2L7oY3_0qZQY").addId("123").build();
            try {
                JestResult jestResult =  client.execute(mg);
                
                System.out.println(jestResult.getJsonString());
                jestResult.getSourceAsObjectList(JsonObject.class).forEach(it -> System.out.println("a"));
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            
            SearchSourceBuilder s = new SearchSourceBuilder();
            s.query(QueryBuilders.matchQuery("id", "cd3d314601a64b4f"));
            
            DeleteByQuery deleteByQuery = new DeleteByQuery.Builder(s.toString()).addIndex("zipkin:span-*").build();
            
            try {
                JestResult result =  client.execute(deleteByQuery);
                System.out.println(result.getJsonString());
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
}
