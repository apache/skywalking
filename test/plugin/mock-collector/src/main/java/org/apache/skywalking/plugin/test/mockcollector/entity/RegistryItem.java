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
package org.apache.skywalking.plugin.test.mockcollector.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.skywalking.apm.network.language.agent.SpanType;

public class RegistryItem {
    /**
     * applicationCode, applicationId
     */
    private final Map<String, Integer> applications;
    /**
     * applicationCode, operationName
     */
    private final Map<String, Set<String>> operationNames;
    /**
     * applicationCode, instanceId
     */
    private final Map<String, List<Integer>> instanceMapping;
    /**
     * applicationCode, count
     */
    private final Map<String, Integer> heartBeats;

    public RegistryItem() {
        applications = new HashMap<>();
        operationNames = new HashMap<>();
        instanceMapping = new HashMap<>();
        heartBeats = new HashMap<>();
    }

    public void registryApplication(Application application) {
        applications.putIfAbsent(application.applicationCode, application.applicationId);
    }

    public void registryOperationName(OperationName operationName) {
        String applicationCode = findApplicationCode(operationName.applicationId);
        Set<String> operationNameList = operationNames.get(applicationCode);
        if (operationNameList == null) {
            operationNameList = new HashSet<>();
            operationNames.put(applicationCode, operationNameList);
        }
        operationNameList.add(operationName.operationName);
    }

    public void registryInstance(Instance instance) {
        String applicationCode = findApplicationCode(instance.applicationId);
        List<Integer> instances = instanceMapping.get(applicationCode);
        if (instances == null) {
            instances = new ArrayList<>();
            instanceMapping.put(applicationCode, instances);
        }

        if (!instances.contains(instance)) {
            instances.add(instance.instanceId);
        }
    }

    public String findApplicationCode(int id) {
        for (Map.Entry<String, Integer> entry : applications.entrySet()) {
            if (entry.getValue() == id) {
                return entry.getKey();
            }
        }
        throw new RuntimeException("Cannot found the code of applicationID[" + id + "].");
    }

    public void registryHeartBeat(HeartBeat heartBeat) {
        for (Map.Entry<String, List<Integer>> entry : instanceMapping.entrySet()) {
            if (entry.getValue().contains(heartBeat.instanceID)) {
                Integer count = heartBeats.get(entry.getKey());
                if (count != null) {
                    heartBeats.put(entry.getKey(), 0);
                    heartBeats.put(entry.getKey(), count++);
                }
            }
        }
    }

    public static class OperationName {
        int applicationId;
        String operationName;

        public OperationName(int applicationId, String operationName) {
            this.applicationId = applicationId;
            this.operationName = operationName;
        }
    }

    public static class Application {
        String applicationCode;
        int applicationId;

        public Application(String applicationCode, int applicationId) {
            this.applicationCode = applicationCode;
            this.applicationId = applicationId;
        }
    }

    public static class Instance {
        int applicationId;
        int instanceId;

        public Instance(int applicationId, int instanceId) {
            this.applicationId = applicationId;
            this.instanceId = instanceId;
        }
    }

    public static class HeartBeat {
        private int instanceID;

        public HeartBeat(int instanceID) {
            this.instanceID = instanceID;
        }
    }

    public Map<String, Integer> getApplications() {
        return applications;
    }

    public Map<String, Set<String>> getOperationNames() {
        return operationNames;
    }

    public Map<String, List<Integer>> getInstanceMapping() {
        return instanceMapping;
    }

    public Map<String, Integer> getHeartBeats() {
        return heartBeats;
    }
}
