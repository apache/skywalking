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

package org.apache.skywalking.apm.plugin.mqtt.v3;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.apache.skywalking.apm.util.StringUtil;

/**
 * Created by @author yuanguohua on 2020/8/21 13:49
 */
public class MqttEnhanceRequiredInfo {
    
    private String brokerServers;
    
    private String topics;
    
    private String qos;
    
    private long startTime;
    
    public void setBrokerServers(String[] brokerServers) {
        this.brokerServers = StringUtil.join(';', brokerServers);
    }
    
    public void setTopics(String[] topics) {
        this.topics = StringUtil.join(';', topics);
    }
    
    public String getBrokerServers() {
        return brokerServers;
    }
    
    public String getTopics() {
        return topics;
    }
    
    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }
    
    public long getStartTime() {
        return startTime;
    }
    
    public String getQos() {
        return qos;
    }
    
    public void setQos(int[] qos) {
        this.qos = Arrays.stream(qos).boxed().map(q -> q.toString()).collect(Collectors.joining(";"));
    }
    
}
