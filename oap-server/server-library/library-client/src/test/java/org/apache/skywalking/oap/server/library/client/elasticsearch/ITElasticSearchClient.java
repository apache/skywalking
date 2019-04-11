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
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.common.xcontent.*;
import org.junit.*;

/**
 * @author peng-yongsheng
 */
public class ITElasticSearchClient {

    @Test
    public void test() throws IOException {
        ElasticSearchClient client = new ElasticSearchClient("localhost:9200", null, null, null);
        client.connect();

        XContentBuilder builder = XContentFactory.jsonBuilder().startObject();
        builder.field("key", "value");
        builder.endObject();
        client.forceInsert("test_index", "201904091521", builder);

        GetResponse response = client.get("test_index", "201904091521");

        Assert.assertTrue(response.getSource().containsKey("key"));
        Assert.assertEquals("value", response.getSource().get("key"));
    }
}
