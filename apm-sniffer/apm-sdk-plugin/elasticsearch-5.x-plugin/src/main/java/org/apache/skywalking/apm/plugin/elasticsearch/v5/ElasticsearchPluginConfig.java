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

import org.apache.skywalking.apm.agent.core.boot.PluginConfig;

public class ElasticsearchPluginConfig {
    public static class Plugin {
        @PluginConfig(root = ElasticsearchPluginConfig.class)
        public static class Elasticsearch {
            /**
             * If true, trace all the DSL(Domain Specific Language) in ElasticSearch access, default is false.
             */
            public static boolean TRACE_DSL = false;

            public static int ELASTICSEARCH_DSL_LENGTH_THRESHOLD = 1024;
        }
    }
}
