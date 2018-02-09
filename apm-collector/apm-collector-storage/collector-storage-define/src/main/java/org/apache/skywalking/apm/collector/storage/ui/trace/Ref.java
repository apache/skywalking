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

/**
 * @author peng-yongsheng
 */
public class Ref {
    private String traceId;
    private String parentSegmentId;
    private Integer parentSpanId;
    private RefType type;

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getParentSegmentId() {
        return parentSegmentId;
    }

    public void setParentSegmentId(String parentSegmentId) {
        this.parentSegmentId = parentSegmentId;
    }

    public Integer getParentSpanId() {
        return parentSpanId;
    }

    public void setParentSpanId(Integer parentSpanId) {
        this.parentSpanId = parentSpanId;
    }

    public RefType getType() {
        return type;
    }

    public void setType(RefType type) {
        this.type = type;
    }
}
