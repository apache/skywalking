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

package org.apache.skywalking.oap.server.admin.inspect.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * The MQE Entity payload an inspect-API client pastes into the public
 * GraphQL {@code execExpression} mutation. Mirrors
 * {@link org.apache.skywalking.oap.server.core.query.input.Entity}'s field
 * set; {@code null} fields are omitted from the JSON so each scope's row
 * includes only the fields {@code Entity.isValid()} requires.
 */
@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MqeEntity {
    private final String scope;
    private final String serviceName;
    private final Boolean normal;
    private final String serviceInstanceName;
    private final String endpointName;
    private final String destServiceName;
    private final Boolean destNormal;
    private final String destServiceInstanceName;
    private final String destEndpointName;
}
