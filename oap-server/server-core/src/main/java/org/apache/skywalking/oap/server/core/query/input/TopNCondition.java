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

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;

/**
 * Top N query condition.
 *
 * @since 8.0.0
 */
@Setter
@Getter
public class TopNCondition {
    /**
     * Metrics name
     */
    private String name;
    /**
     * See {@link Entity}
     */
    private String parentService;
    /**
     * Normal service is the service having installed agent or metrics reported directly. Unnormal service is
     * conjectural service, usually detected by the agent.
     */
    private boolean normal;
    /**
     * Indicate the metrics entity scope. Because this is a top list, don't need to set the Entity like the
     * MetricsCondition. Only accept scope = {@link Scope#Service} {@link Scope#ServiceInstance} and {@link
     * Scope#Endpoint}, ignore others due to those are pointless.
     */
    private Scope scope;
    private int topN;
    private Order order;
}
