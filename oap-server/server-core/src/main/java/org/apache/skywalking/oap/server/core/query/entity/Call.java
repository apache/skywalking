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

package org.apache.skywalking.oap.server.core.query.entity;

import java.util.*;
import lombok.*;
import org.apache.skywalking.oap.server.core.source.DetectPoint;

/**
 * @author peng-yongsheng
 */
@Getter
@Setter
public class Call {
    private Integer source;
    private Integer target;
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

    public void setSource(int source) {
        this.source = source;
    }

    public void setTarget(int target) {
        this.target = target;
    }

    public void addSourceComponentId(int componentId) {
        sourceComponentIDs.add(componentId);
    }

    public void addTargetComponentId(int componentId) {
        targetComponentIDs.add(componentId);
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

    @Setter
    @Getter
    public static class CallDetail {
        private String id;
        private Integer source;
        private Integer target;
        private DetectPoint detectPoint;
        private Integer componentId;
    }
}
