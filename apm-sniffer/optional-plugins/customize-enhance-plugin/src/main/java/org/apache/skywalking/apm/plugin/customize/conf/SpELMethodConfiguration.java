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

package org.apache.skywalking.apm.plugin.customize.conf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SpEL custom enhancement configuration.
 *
 * @author zhaoyuguang
 */

public class SpELMethodConfiguration extends DefaultMethodConfiguration {

    private List<String> operationNameSuffixes = new ArrayList<String>();
    private Map<String, String> tags = new HashMap<String, String>();
    private Map<String, String> logs = new HashMap<String, String>();

    public List<String> getOperationNameSuffixes() {
        return operationNameSuffixes;
    }

    public void setOperationNameSuffixes(List<String> operationNameSuffixes) {
        this.operationNameSuffixes = operationNameSuffixes;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }

    public Map<String, String> getLogs() {
        return logs;
    }

    public void setLogs(Map<String, String> logs) {
        this.logs = logs;
    }
}
