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
 */

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.client;

import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.common.xcontent.XContentBuilder;

/**
 * @author peng-yongsheng
 * @author kezhenxu94
 */
public class ElasticSearch7InsertRequest extends IndexRequest implements InsertRequest {

    public ElasticSearch7InsertRequest(String index, String id) {
        super(index);
        id(id);
    }

    @Override
    public ElasticSearch7InsertRequest source(XContentBuilder sourceBuilder) {
        super.source(sourceBuilder);
        return this;
    }
}
