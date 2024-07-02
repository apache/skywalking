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

package org.apache.skywalking.oap.server.fetcher.cilium.handler;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.cilium.api.flow.Endpoint;
import io.vavr.Tuple;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.skywalking.oap.server.library.util.FieldsHelper;

@Getter
@Setter
@ToString
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ServiceMetadata {
    @EqualsAndHashCode.Include
    private String serviceName;
    @EqualsAndHashCode.Include
    private String serviceInstanceName;

    public ServiceMetadata(Endpoint endpoint) {
        FieldsHelper.forClass(this.getClass()).inflate(parseEndpointToStruct(endpoint), this);
    }

    private Struct parseEndpointToStruct(Endpoint endpoint) {
        final Struct.Builder builder = Struct.newBuilder();

        // Convert Labels
        final Struct.Builder labelsStruct = Struct.newBuilder();
        endpoint.getLabelsList().stream()
            .map(label -> label.split("=", 2))
            .forEach(split -> {
                if (split.length == 1) {
                    addingLabel(labelsStruct, split[0], "");
                    return;
                }
                addingLabel(labelsStruct, split[0], split[1]);
            });
        builder.putFields("LABELS", Value.newBuilder().setStructValue(labelsStruct.build()).build());

        // Convert Workloads
        final Struct.Builder workloadsStruct = Struct.newBuilder();
        endpoint.getWorkloadsList().stream()
            .map(workload -> Tuple.of(workload.getKind(), workload.getName()))
            .forEach(split -> {
                workloadsStruct.putFields(split._1, Value.newBuilder().setStringValue(split._2).build());
            });
        builder.putFields("WORKLOADS", Value.newBuilder().setStructValue(workloadsStruct.build()).build());

        // Adding other metadata
        builder.putFields("NAMESPACE", Value.newBuilder().setStringValue(endpoint.getNamespace()).build());
        builder.putFields("NAME", Value.newBuilder().setStringValue(endpoint.getPodName()).build());

        return builder.build();
    }

    private void addingLabel(Struct.Builder builder, String key, String value) {
        final Value val = Value.newBuilder().setStringValue(value).build();
        builder.putFields(key, val);
        // remove the prefix "k8s:" from the key
        if (key.indexOf("k8s:") == 0) {
            builder.putFields(key.substring(4), val);
        }
    }
}
