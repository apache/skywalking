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

package org.apache.skywalking.oap.server.core.query.input;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.type.Pagination;
import org.apache.skywalking.oap.server.core.query.type.QueryOrder;
import org.apache.skywalking.oap.server.core.query.type.TraceState;

@Getter
@Setter
@ToString
public class TraceQueryConditionByName {
    private ServiceCondition service;
    private InstanceCondition instance;
    private EndpointCondition endpoint;
    private String traceId;
    private Duration queryDuration;
    private int minTraceDuration;
    private int maxTraceDuration;
    private TraceState traceState;
    private QueryOrder queryOrder;
    private Pagination paging;
    private List<Tag> tags;

    public String getServiceId() {
        if (service != null) {
            return service.getServiceId();
        } else {
            return null;
        }
    }

    public String getServiceInstanceId() {
        if (instance != null) {
            return instance.getInstanceId();
        } else {
            return null;
        }
    }

    public String getEndpointId() {
        if (endpoint != null) {
            return endpoint.getEndpointId();
        } else {
            return null;
        }
    }
}
