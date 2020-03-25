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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.Getter;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.UnexpectedException;

public class RelationDefineUtil {
    public static String buildEntityId(RelationDefine define) {
        return String.valueOf(define.source) + Const.ID_SPLIT + String.valueOf(
            define.dest) + Const.ID_SPLIT + String.valueOf(define.componentId);
    }

    /**
     * @return 1. sourceId 2. destId 3. componentId
     */
    public static RelationDefine splitEntityId(String entityId) {
        String[] parts = entityId.split(Const.ID_SPLIT);
        if (parts.length != 3) {
            throw new RuntimeException("Illegal Service/Endpoint Relation entity id");
        }
        return new RelationDefine(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    public static String buildEndpointEntityId(EndpointRelationDefine define) {
        return define.sourceServiceId
            + Const.ID_SPLIT
            + Base64.getEncoder().encode(define.source.getBytes(StandardCharsets.UTF_8))
            + Const.ID_SPLIT
            + define.destServiceId
            + Const.ID_SPLIT
            + Base64.getEncoder().encode(define.dest.getBytes(StandardCharsets.UTF_8))
            + Const.ID_SPLIT
            + define.componentId;
    }

    public static EndpointRelationDefine splitEndpointEntityId(String entityId) {
        String[] parts = entityId.split(Const.ID_SPLIT);
        if (parts.length != 5) {
            throw new UnexpectedException("Illegal Service/Endpoint Relation entity id, " + entityId);
        }
        return new EndpointRelationDefine(
            Integer.parseInt(parts[0]),
            new String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8),
            Integer.parseInt(parts[2]),
            new String(Base64.getDecoder().decode(parts[3]), StandardCharsets.UTF_8),
            Integer.parseInt(parts[4])
        );
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

    @Getter
    public static class EndpointRelationDefine {
        private int sourceServiceId;
        private String source;
        private int destServiceId;
        private String dest;
        private int componentId;

        public EndpointRelationDefine(final int sourceServiceId,
                                      final String source,
                                      final int destServiceId,
                                      final String dest,
                                      final int componentId) {
            this.sourceServiceId = sourceServiceId;
            this.source = source;
            this.destServiceId = destServiceId;
            this.dest = dest;
            this.componentId = componentId;
        }
    }
}
