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
package org.apache.skywalking.plugin.test.agent.tool.validator.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RegistryItemsForRead implements RegistryItems {
    private List<Map<String, String>> applications;
    private List<Map<String, String>> instances;
    private List<Map<String, List<String>>> operationNames;

    public List<Map<String, String>> getApplications() {
        return applications;
    }

    public void setApplications(List<Map<String, String>> applications) {
        this.applications = applications;
    }

    public List<Map<String, String>> getInstances() {
        return instances;
    }

    public void setInstances(List<Map<String, String>> instances) {
        this.instances = instances;
    }

    public List<Map<String, List<String>>> getOperationNames() {
        return operationNames;
    }

    public void setOperationNames(
        List<Map<String, List<String>>> operationNames) {
        this.operationNames = operationNames;
    }

    @Override
    public List<RegistryApplication> applications() {
        if (this.applications == null) {
            return null;
        }

        List<RegistryApplication> registryApplications = new ArrayList<>();
        for (Map<String, String> registryApplication : applications) {
            String applicationCode = new ArrayList<String>(registryApplication.keySet()).get(0);
            String express = String.valueOf(registryApplication.get(applicationCode));
            registryApplications.add(new RegistryApplication.Impl(applicationCode, express));
        }
        return registryApplications;
    }

    @Override
    public List<RegistryInstance> instances() {
        if (this.instances == null) {
            return null;
        }

        List<RegistryInstance> registryInstances = new ArrayList<>();
        instances.forEach((registryInstance) -> {
            String applicationCode = new ArrayList<String>(registryInstance.keySet()).get(0);
            String express = String.valueOf(registryInstance.get(applicationCode));
            registryInstances.add(new RegistryInstance.Impl(applicationCode, express));
        });
        return registryInstances;
    }

    @Override
    public List<RegistryOperationName> operationNames() {
        if (this.operationNames == null) {
            return null;
        }

        List<RegistryOperationName> registryOperationNames = new ArrayList<>();
        operationNames.forEach((registryInstance) -> {
            String applicationCode = new ArrayList<String>(registryInstance.keySet()).get(0);
            List<String> express = registryInstance.get(applicationCode);
            registryOperationNames.add(new RegistryOperationName.Impl(applicationCode, express));
        });
        return registryOperationNames;
    }
}
