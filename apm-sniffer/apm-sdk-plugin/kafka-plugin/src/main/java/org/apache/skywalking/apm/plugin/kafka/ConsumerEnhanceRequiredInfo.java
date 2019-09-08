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

package org.apache.skywalking.apm.plugin.kafka;

import java.util.Collection;
import java.util.List;
import org.apache.skywalking.apm.util.StringUtil;

public class ConsumerEnhanceRequiredInfo {
    private String brokerServers;
    private String topics;
    private String groupId;
    private long startTime;

    public void setBrokerServers(List<String> brokerServers) {
        this.brokerServers =  StringUtil.join(';', brokerServers.toArray(new String[0]));
    }

    public void setTopics(Collection<String> topics) {
        this.topics = StringUtil.join(';', topics.toArray(new String[0]));
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getBrokerServers() {
        return brokerServers;
    }

    public String getTopics() {
        return topics;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getStartTime() {
        return startTime;
    }
}
