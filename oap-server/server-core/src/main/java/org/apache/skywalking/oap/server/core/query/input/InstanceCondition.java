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

import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;

@Setter
@Getter
@ToString
public class InstanceCondition {
    private String serviceName;
    private String instanceName;
    @Nullable
    private String layer;

    /**
     * Get the instance ID based on the service name, instance name and {@link Layer} name.
     * The layer can be null, in which case it defaults to a normal layer.
     * Otherwise, it uses the provided layer to determine if the service is normal or not.
     * The un-normal layer includes VIRTUAL_DATABASE/VIRTUAL_MQ/VIRTUAL_GATEWAY, etc.
     * @return instance ID
     */
    public String getInstanceId() {
        // default to true if service layer is not provided
        return IDManager.ServiceInstanceID.buildId(
            IDManager.ServiceID.buildId(
                serviceName,
                layer == null || Layer.nameOf(layer).isNormal()
            ),
            instanceName
        );
    }
}
