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

package org.apache.skywalking.oap.server.core.query.type;

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.source.DetectPoint;

@Getter
@Setter
public class Call {
    private String source;
    private String target;
    private List<String> sourceComponents;
    private List<String> targetComponents;
    private String id;
    private List<DetectPoint> detectPoints;

    private List<Integer> sourceComponentIDs;
    private List<Integer> targetComponentIDs;

    public Call() {
        sourceComponents = new ArrayList<>();
        targetComponents = new ArrayList<>();
        detectPoints = new ArrayList<>();
    }

    public void setSource(String source) {
        this.source = source;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public void addSourceComponent(String component) {
        if (!sourceComponents.contains(component)) {
            sourceComponents.add(component);
        }
    }

    public void addTargetComponent(String component) {
        if (!targetComponents.contains(component)) {
            targetComponents.add(component);
        }
    }

    public void addDetectPoint(DetectPoint point) {
        if (!detectPoints.contains(point)) {
            detectPoints.add(point);
        }
    }

    @Setter(AccessLevel.PRIVATE)
    @Getter
    public static class CallDetail {
        private String id;
        private String source;
        private String target;
        private DetectPoint detectPoint;
        private Integer componentId;

        public void buildFromServiceRelation(String entityId, int componentId, DetectPoint detectPoint) {
            final IDManager.ServiceID.ServiceRelationDefine serviceRelationDefine
                = IDManager.ServiceID.analysisRelationId(entityId);

            this.setId(entityId);
            this.setSource(serviceRelationDefine.getSourceId());
            this.setTarget(serviceRelationDefine.getDestId());
            this.setComponentId(componentId);
            this.setDetectPoint(detectPoint);
        }

        public void buildFromInstanceRelation(String entityId, int componentId, DetectPoint detectPoint) {
            final IDManager.ServiceInstanceID.ServiceInstanceRelationDefine serviceRelationDefine
                = IDManager.ServiceInstanceID.analysisRelationId(entityId);

            this.setId(entityId);
            this.setSource(serviceRelationDefine.getSourceId());
            this.setTarget(serviceRelationDefine.getDestId());
            this.setComponentId(componentId);
            this.setDetectPoint(detectPoint);
        }

        public void buildFromEndpointRelation(String entityId, DetectPoint detectPoint) {
            this.setId(entityId);

            final IDManager.EndpointID.EndpointRelationDefine endpointRelationDefine = IDManager.EndpointID.analysisRelationId(
                entityId);
            final IDManager.ServiceID.ServiceIDDefinition sourceService = IDManager.ServiceID.analysisId(
                endpointRelationDefine.getSourceServiceId());

            this.setDetectPoint(detectPoint);
            this.setSource(
                IDManager.EndpointID.buildId(
                    endpointRelationDefine.getSourceServiceId(), endpointRelationDefine.getSource())
            );
            this.setTarget(
                IDManager.EndpointID.buildId(
                    endpointRelationDefine.getDestServiceId(), endpointRelationDefine.getDest())
            );
            this.setComponentId(0);
        }
    }
}
