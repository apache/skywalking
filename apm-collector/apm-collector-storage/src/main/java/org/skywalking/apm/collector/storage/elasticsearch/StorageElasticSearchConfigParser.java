/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.storage.elasticsearch;

import java.util.Map;
import org.skywalking.apm.collector.core.config.ConfigParseException;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.core.util.ObjectUtils;
import org.skywalking.apm.collector.core.util.StringUtils;

/**
 * @author peng-yongsheng
 */
public class StorageElasticSearchConfigParser implements ModuleConfigParser {

    private static final String CLUSTER_NAME = "cluster_name";
    private static final String CLUSTER_TRANSPORT_SNIFFER = "cluster_transport_sniffer";
    private static final String CLUSTER_NODES = "cluster_nodes";
    private static final String INDEX_SHARDS_NUMBER = "index_shards_number";
    private static final String INDEX_REPLICAS_NUMBER = "index_replicas_number";

    @Override public void parse(Map config) throws ConfigParseException {
        if (ObjectUtils.isNotEmpty(config) && StringUtils.isNotEmpty(config.get(CLUSTER_NAME))) {
            StorageElasticSearchConfig.CLUSTER_NAME = (String)config.get(CLUSTER_NAME);
        }
        if (ObjectUtils.isNotEmpty(config) && StringUtils.isNotEmpty(config.get(CLUSTER_TRANSPORT_SNIFFER))) {
            StorageElasticSearchConfig.CLUSTER_TRANSPORT_SNIFFER = (Boolean)config.get(CLUSTER_TRANSPORT_SNIFFER);
        }
        if (ObjectUtils.isNotEmpty(config) && StringUtils.isNotEmpty(config.get(CLUSTER_NODES))) {
            StorageElasticSearchConfig.CLUSTER_NODES = (String)config.get(CLUSTER_NODES);
        }
        if (ObjectUtils.isNotEmpty(config) && StringUtils.isNotEmpty(config.get(INDEX_SHARDS_NUMBER))) {
            StorageElasticSearchConfig.INDEX_SHARDS_NUMBER = (Integer)config.get(INDEX_SHARDS_NUMBER);
        } else {
            StorageElasticSearchConfig.INDEX_SHARDS_NUMBER = 2;
        }
        if (ObjectUtils.isNotEmpty(config) && StringUtils.isNotEmpty(config.get(INDEX_REPLICAS_NUMBER))) {
            StorageElasticSearchConfig.INDEX_REPLICAS_NUMBER = (Integer)config.get(INDEX_REPLICAS_NUMBER);
        } else {
            StorageElasticSearchConfig.INDEX_REPLICAS_NUMBER = 0;
        }
    }
}
