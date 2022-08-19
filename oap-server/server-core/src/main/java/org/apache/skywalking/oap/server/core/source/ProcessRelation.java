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
import org.apache.commons.lang3.StringUtils;
import org.apache.skywalking.oap.server.core.analysis.IDManager;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.PROCESS_RELATION;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.PROCESS_RELATION_CATALOG_NAME;

@ScopeDeclaration(id = PROCESS_RELATION, name = "ProcessRelation", catalog = PROCESS_RELATION_CATALOG_NAME)
@ScopeDefaultColumn.VirtualColumnDefinition(fieldName = "entityId", columnName = "entity_id", isID = true, type = String.class)
public class ProcessRelation extends Source {
    private String entityId;

    @Override
    public int scope() {
        return PROCESS_RELATION;
    }

    @Override
    public String getEntityId() {
        if (StringUtils.isEmpty(entityId)) {
            entityId = IDManager.ProcessID.buildRelationId(
                new IDManager.ProcessID.ProcessRelationDefine(
                    sourceProcessId,
                    destProcessId
                )
            );
        }
        return entityId;
    }

    @Getter
    @Setter
    private String instanceId;
    @Getter
    @Setter
    private String sourceProcessId;
    @Getter
    @Setter
    private String destProcessId;
    @Getter
    @Setter
    private DetectPoint detectPoint;
    @Setter
    @Getter
    private int componentId;
}
