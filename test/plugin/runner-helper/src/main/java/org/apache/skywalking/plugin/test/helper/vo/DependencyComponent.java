/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.plugin.test.helper.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class DependencyComponent {
    private String image;
    private String hostname;
    private String version;
    private boolean removeOnExit = false;
    private List<String> startScript;
    private List<String> links;
    private List<String> expose;
    private List<String> entrypoint;
    private List<String> environment;
    private List<String> dependsOn;
    private List<String> healthcheck;

    //make sure that depends_on can be set correctly
    public void setDepends_on(List<String> dependsOn) {
        this.dependsOn = dependsOn;
    }
}
