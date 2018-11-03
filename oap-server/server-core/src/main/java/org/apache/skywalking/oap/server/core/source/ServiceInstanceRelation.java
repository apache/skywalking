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

package org.apache.skywalking.oap.server.core.source;

import lombok.*;
import org.apache.skywalking.oap.server.core.Const;

/**
 * @author peng-yongsheng
 */
public class ServiceInstanceRelation extends Source {

    @Override public Scope scope() {
        return Scope.ServiceInstanceRelation;
    }

    @Override public String getEntityId() {
        return String.valueOf(sourceServiceInstanceId) + Const.ID_SPLIT + String.valueOf(destServiceInstanceId) + Const.ID_SPLIT + String.valueOf(componentId);
    }

    @Getter @Setter private int sourceServiceInstanceId;
    @Getter @Setter private int sourceServiceId;
    @Getter @Setter private String sourceServiceName;
    @Getter @Setter private String sourceServiceInstanceName;
    @Getter @Setter private int destServiceInstanceId;
    @Getter @Setter private int destServiceId;
    @Getter @Setter private String destServiceName;
    @Getter @Setter private String destServiceInstanceName;
    @Getter @Setter private String endpoint;
    @Getter @Setter private int componentId;
    @Getter @Setter private int latency;
    @Getter @Setter private boolean status;
    @Getter @Setter private int responseCode;
    @Getter @Setter private RequestType type;
    @Getter @Setter private DetectPoint detectPoint;
}
