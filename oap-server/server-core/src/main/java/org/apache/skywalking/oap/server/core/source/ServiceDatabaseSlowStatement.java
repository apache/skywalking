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

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.Const;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.SERVICE_CATALOG_NAME;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.SERVICE_DATABASE_SLOW_STATEMENT;

@ScopeDeclaration(id = SERVICE_DATABASE_SLOW_STATEMENT, name = "ServiceDatabaseSlowStatement", catalog = SERVICE_CATALOG_NAME)
public class ServiceDatabaseSlowStatement extends Source {

    @Getter
    @Setter
    private String id;

    @Getter
    @Setter
    @ScopeDefaultColumn.DefinedByField(columnName = "service_id")
    @ScopeDefaultColumn.BanyanDB(shardingKeyIdx = 0)
    private String serviceId;

    @Getter
    @Setter
    private String statement;

    @Getter
    @Setter
    private long latency;

    @Getter
    @Setter
    private String traceId;

    @Getter
    @Setter
    private long timestamp;

    @Override
    public int scope() {
        return DefaultScopeDefine.SERVICE_DATABASE_SLOW_STATEMENT;
    }

    @Override
    public String getEntityId() {
        return Const.EMPTY_STRING;
    }

}
