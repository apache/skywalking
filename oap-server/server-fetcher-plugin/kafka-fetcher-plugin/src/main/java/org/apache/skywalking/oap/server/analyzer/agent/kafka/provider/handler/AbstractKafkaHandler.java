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

package org.apache.skywalking.oap.server.analyzer.agent.kafka.provider.handler;

import org.apache.commons.lang3.StringUtils;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.analyzer.agent.kafka.module.KafkaFetcherConfig;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

public abstract class AbstractKafkaHandler implements KafkaHandler {
    protected KafkaFetcherConfig config;

    public AbstractKafkaHandler(ModuleManager manager, KafkaFetcherConfig config) {
        this.config = config;
    }

    @Override
    public String getTopic() {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotBlank(config.getMm2SourceAlias())) {
            sb.append(config.getMm2SourceAlias()).append(config.getMm2SourceSeparator());
        }

        if (StringUtil.isNotBlank(config.getNamespace())) {
            sb.append(config.getNamespace()).append("-");
        }
        sb.append(getPlainTopic());
        return sb.toString();
    }

    protected abstract String getPlainTopic();

    @Override
    public String getConsumePartitions() {
        return config.getConsumePartitions();
    }

}
