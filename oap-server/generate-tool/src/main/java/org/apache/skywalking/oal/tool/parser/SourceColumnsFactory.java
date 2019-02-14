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

package org.apache.skywalking.oal.tool.parser;

import java.util.*;

public class SourceColumnsFactory {
    public static List<SourceColumn> getColumns(String source) {
        List<SourceColumn> columnList;
        SourceColumn idColumn;
        switch (source) {
            case "All":
                return new LinkedList<>();
            case "Service":
                columnList = new LinkedList<>();
                // Service id;
                idColumn = new SourceColumn("entityId", "entity_id", String.class, true);
                columnList.add(idColumn);
                return columnList;
            case "ServiceInstance":
                columnList = new LinkedList<>();
                // Service instance id;
                idColumn = new SourceColumn("entityId", "entity_id", String.class, true);
                columnList.add(idColumn);
                SourceColumn serviceIdColumn = new SourceColumn("serviceId", "service_id", int.class, false);
                columnList.add(serviceIdColumn);
                return columnList;
            case "Endpoint":
                columnList = new LinkedList<>();
                // Endpoint id;
                idColumn = new SourceColumn("entityId", "entity_id", String.class, true);
                columnList.add(idColumn);
                serviceIdColumn = new SourceColumn("serviceId", "service_id", int.class, false);
                columnList.add(serviceIdColumn);
                SourceColumn serviceInstanceIdColumn = new SourceColumn("serviceInstanceId", "service_instance_id", int.class, false);
                columnList.add(serviceInstanceIdColumn);
                return columnList;
            case "ServiceInstanceJVMCPU":
            case "ServiceInstanceJVMMemory":
            case "ServiceInstanceJVMMemoryPool":
            case "ServiceInstanceJVMGC":
                columnList = new LinkedList<>();
                // Service instance id;
                idColumn = new SourceColumn("entityId", "entity_id", String.class, true);
                columnList.add(idColumn);
                serviceInstanceIdColumn = new SourceColumn("serviceInstanceId", "service_instance_id", int.class, false);
                columnList.add(serviceInstanceIdColumn);
                return columnList;
            case "ServiceRelation":
                columnList = new LinkedList<>();
                SourceColumn sourceService = new SourceColumn("entityId", "entity_id", String.class, true);
                columnList.add(sourceService);
                return columnList;
            case "ServiceInstanceRelation":
                columnList = new LinkedList<>();
                sourceService = new SourceColumn("entityId", "entity_id", String.class, true);
                columnList.add(sourceService);
                sourceService = new SourceColumn("sourceServiceId", "source_service_id", int.class, false);
                columnList.add(sourceService);
                SourceColumn destService = new SourceColumn("destServiceId", "destServiceId", int.class, false);
                columnList.add(destService);

                return columnList;
            case "EndpointRelation":
                columnList = new LinkedList<>();
                SourceColumn sourceEndpointColumn = new SourceColumn("entityId", "entity_id", String.class, true);
                columnList.add(sourceEndpointColumn);
                sourceService = new SourceColumn("serviceId", "service_id", int.class, false);
                columnList.add(sourceService);
                destService = new SourceColumn("childServiceId", "child_service_id", int.class, false);
                columnList.add(destService);
                SourceColumn sourceServiceInstance = new SourceColumn("serviceInstanceId", "service_instance_id", int.class, false);
                columnList.add(sourceServiceInstance);
                SourceColumn destServiceInstance = new SourceColumn("childServiceInstanceId", "child_service_instance_id", int.class, false);
                columnList.add(destServiceInstance);
                return columnList;
            case "DatabaseAccess":
                columnList = new LinkedList<>();
                // Service id;
                idColumn = new SourceColumn("entityId", "entity_id", String.class, true);
                columnList.add(idColumn);
                return columnList;
            default:
                throw new IllegalArgumentException("Illegal source :" + source);
        }
    }
}
