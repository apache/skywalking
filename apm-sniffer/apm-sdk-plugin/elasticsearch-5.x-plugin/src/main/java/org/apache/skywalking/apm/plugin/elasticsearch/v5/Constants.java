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

package org.apache.skywalking.apm.plugin.elasticsearch.v5;

/**
 * @author oatiz.
 */
class Constants {

    static final String DB_TYPE = "Elasticsearch";

    static final String ELASTICSEARCH_DB_OP_PREFIX = "Elasticsearch/";

    static final String BASE_FUTURE_METHOD = "actionGet";

    static final String ES_NODE = "node.address";

    static final String ES_INDEX = "es.indices";

    static final String ES_TYPE = "es.types";

    static final String ES_TOOK_MILLIS = "es.took_millis";

    static final String ES_TOTAL_HITS = "es.total_hits";

    static final String ES_INGEST_TOOK_MILLIS = "es.ingest_took_millis";

}
