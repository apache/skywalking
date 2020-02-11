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

import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;

public class ElasticSearchEnhanceInfo {
    /**
     * elasticsearch cluster name
     */
    private String clusterName;
    /**
     * elasticsearch indices
     */
    private String indices;
    /**
     * elasticsearch types
     */
    private String types;
    /**
     * operation type: INDEX, CREATE, UPDATE, DELETE, BULK-bulkNum, defaultActionName
     */
    private String opType;
    /**
     * source dsl
     */
    private String source;

    private EnhancedInstance transportAddressHolder;

    public String transportAddresses() {
        return ((TransportAddressCache) transportAddressHolder.getSkyWalkingDynamicField()).transportAddress();
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public String getIndices() {
        return indices;
    }

    public void setIndices(String indices) {
        this.indices = indices;
    }

    public String getTypes() {
        return types;
    }

    public void setTypes(String types) {
        this.types = types;
    }

    public String getOpType() {
        return opType;
    }

    public void setOpType(String opType) {
        this.opType = opType;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public void setTransportAddressHolder(EnhancedInstance service) {
        this.transportAddressHolder = service;
    }
}
