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

import org.apache.skywalking.apm.agent.core.context.tag.AbstractTag;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;

/**
 * @author oatiz.
 */
class Constants {

    static final String DB_TYPE = "Elasticsearch";

    static final String ELASTICSEARCH_DB_OP_PREFIX = "Elasticsearch/";

    static final String BASE_FUTURE_METHOD = "actionGet";

    static final AbstractTag<String> ES_NODE = Tags.ofKey("node.address");

    static final AbstractTag<String> ES_INDEX = Tags.ofKey("es.indices");

    static final AbstractTag<String> ES_TYPE = Tags.ofKey("es.types");

    static final AbstractTag<String> ES_TOOK_MILLIS = Tags.ofKey("es.took_millis");

    static final AbstractTag<String> ES_TOTAL_HITS = Tags.ofKey("es.total_hits");

    static final AbstractTag<String> ES_INGEST_TOOK_MILLIS = Tags.ofKey("es.ingest_took_millis");

}
