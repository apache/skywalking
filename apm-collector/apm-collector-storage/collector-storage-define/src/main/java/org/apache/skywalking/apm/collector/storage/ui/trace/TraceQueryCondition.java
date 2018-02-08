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

package org.apache.skywalking.apm.collector.storage.ui.trace;

import org.apache.skywalking.apm.collector.storage.ui.common.Duration;
import org.apache.skywalking.apm.collector.storage.ui.common.Pagination;

/**
 * @author peng-yongsheng
 */
public class TraceQueryCondition {
    private int applicationId;
    private String traceId;
    private String operationName;
    private Duration queryDuration;
    private int minTraceDuration;
    private int maxTraceDuration;
    private Pagination paging;

    public int getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(int applicationId) {
        this.applicationId = applicationId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getOperationName() {
        return operationName;
    }

    public void setOperationName(String operationName) {
        this.operationName = operationName;
    }

    public Duration getQueryDuration() {
        return queryDuration;
    }

    public void setQueryDuration(Duration queryDuration) {
        this.queryDuration = queryDuration;
    }

    public int getMinTraceDuration() {
        return minTraceDuration;
    }

    public void setMinTraceDuration(int minTraceDuration) {
        this.minTraceDuration = minTraceDuration;
    }

    public int getMaxTraceDuration() {
        return maxTraceDuration;
    }

    public void setMaxTraceDuration(int maxTraceDuration) {
        this.maxTraceDuration = maxTraceDuration;
    }

    public Pagination getPaging() {
        return paging;
    }

    public void setPaging(Pagination paging) {
        this.paging = paging;
    }
}
