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

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.SERVICE_INSTANCE_CATALOG_NAME;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.SERVICE_INSTANCE_JVM_GC;

/**
 * @author peng-yongsheng
 */
@ScopeDeclaration(id = SERVICE_INSTANCE_JVM_GC, name = "ServiceInstanceJVMGC", catalog = SERVICE_INSTANCE_CATALOG_NAME)
public class ServiceInstanceJVMGC extends Source {
    @Override public int scope() {
        return DefaultScopeDefine.SERVICE_INSTANCE_JVM_GC;
    }

    @Override public String getEntityId() {
        return String.valueOf(id);
    }

    @Getter @Setter private int id;
    @Getter @Setter private String name;
    @Getter @Setter private String serviceName;
    @Getter @Setter private int serviceId;
    @Getter @Setter private GCPhrase phrase;
    @Getter @Setter private long time;
    @Getter @Setter private long count;
}
