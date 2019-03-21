/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.server.library.client.elasticsearch;

import java.io.IOException;
import org.apache.skywalking.oap.server.library.client.ClientException;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.*;
import org.junit.Assert;

/**
 * @author peng-yongsheng
 */
public class ElasticSearchClientTestCase {

    public static void main(String[] args) throws IOException, ClientException {
        Settings settings = Settings.builder()
            .put("number_of_shards", 2)
            .put("number_of_replicas", 0)
            .build();

        XContentBuilder builder = XContentFactory.jsonBuilder();
        builder.startObject()
            .startObject("_all")
            .field("enabled", false)
            .endObject()
            .startObject("properties")
            .startObject("column1")
            .field("type", "text")
            .endObject()
            .endObject();
        builder.endObject();

        ElasticSearchClient client = new ElasticSearchClient("localhost:9200", null, null, null);
        client.connect();

        String indexName = "test";
        client.createIndex(indexName, settings, builder);
        Assert.assertTrue(client.isExistsIndex(indexName));
        client.deleteIndex(indexName);
        Assert.assertFalse(client.isExistsIndex(indexName));


        client.shutdown();
    }
}
