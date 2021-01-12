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
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.NodeType;
import org.apache.skywalking.oap.server.core.config.NamingControl;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.DATABASE_SLOW_STATEMENT;

@ScopeDeclaration(id = DATABASE_SLOW_STATEMENT, name = "DatabaseSlowStatement")
@RequiredArgsConstructor
public class DatabaseSlowStatement extends Source {
    private final NamingControl namingControl;

    @Getter
    @Setter
    private String id;
    @Getter
    @Setter
    private String serviceName;
    @Getter
    @Setter
    private NodeType type;
    @Getter
    private String databaseServiceId;
    @Getter
    @Setter
    private String statement;
    @Getter
    @Setter
    private long latency;
    @Getter
    @Setter
    private String traceId;

    @Override
    public int scope() {
        return DefaultScopeDefine.DATABASE_SLOW_STATEMENT;
    }

    @Override
    public String getEntityId() {
        return Const.EMPTY_STRING;
    }

    @Override
    public void prepare() {
        super.prepare();
        if (this.type == null) {
            this.type = NodeType.Database;
        }
        this.serviceName = namingControl.formatServiceName(serviceName);
        this.databaseServiceId = IDManager.ServiceID.buildId(serviceName, type);
    }
}
