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

package org.apache.skywalking.e2e.topo;

import java.util.List;

/**
 * @author kezhenxu94
 */
public class Call {
    private String id;
    private String source;
    private List<String> detectPoints;
    private String target;

    public String getId() {
        return id;
    }

    public Call setId(String id) {
        this.id = id;
        return this;
    }

    public String getSource() {
        return source;
    }

    public Call setSource(String source) {
        this.source = source;
        return this;
    }

    public List<String> getDetectPoints() {
        return detectPoints;
    }

    public Call setDetectPoints(List<String> detectPoints) {
        this.detectPoints = detectPoints;
        return this;
    }

    public String getTarget() {
        return target;
    }

    public Call setTarget(String target) {
        this.target = target;
        return this;
    }
}
