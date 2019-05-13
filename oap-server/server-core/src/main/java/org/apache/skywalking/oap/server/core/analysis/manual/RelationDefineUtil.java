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

package org.apache.skywalking.oap.server.core.analysis.manual;

import lombok.Getter;
import org.apache.skywalking.oap.server.core.Const;

public class RelationDefineUtil {
    public static String buildEntityId(RelationDefine define) {
        return String.valueOf(define.source)
            + Const.ID_SPLIT + String.valueOf(define.dest)
            + Const.ID_SPLIT + String.valueOf(define.componentId);
    }

    /**
     * @param entityId
     * @return 1. sourceId 2. destId 3. componentId
     */
    public static RelationDefine splitEntityId(String entityId) {
        String[] parts = entityId.split(Const.ID_SPLIT);
        if (parts.length != 3) {
            throw new RuntimeException("Illegal Service/Endpoint Relation entity id");
        }
        return new RelationDefine(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    @Getter
    public static class RelationDefine {
        private int source;
        private int dest;
        private int componentId;

        public RelationDefine(int source, int dest, int componentId) {
            this.source = source;
            this.dest = dest;
            this.componentId = componentId;
        }
    }
}
