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

package org.apache.skywalking.apm.plugin.solrj;

import org.apache.skywalking.apm.agent.core.boot.PluginConfig;

public class SolrJPluginConfig {
    public static class Plugin {
        @PluginConfig(root = SolrJPluginConfig.class)
        public static class SolrJ {
            /**
             * If true, trace all the query parameters(include deleteByIds and deleteByQuery) in Solr query request,
             * default is false.
             */
            public static boolean TRACE_STATEMENT = false;

            /**
             * If true, trace all the operation parameters in Solr request, default is false.
             */
            public static boolean TRACE_OPS_PARAMS = false;
        }
    }
}
