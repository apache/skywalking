/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.skywalking.apm.plugin.elasticsearch.v5;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.elasticsearch.common.transport.TransportAddress;

public class TransportAddressCache {

    private List<TransportAddress> transportAddresses = new ArrayList<TransportAddress>();
    private String transportAddressesStr;

    public synchronized void addDiscoveryNode(TransportAddress... transportAddress) {
        transportAddresses.addAll(Arrays.asList(transportAddress));
        transportAddressesStr = format();
    }

    public synchronized void removeDiscoveryNode(TransportAddress transportAddress) {
        List<TransportAddress> nodesBuilder = new ArrayList<TransportAddress>();

        for (TransportAddress otherNode : transportAddresses) {
            if (!otherNode.getAddress().equals(transportAddress.getAddress())) {
                nodesBuilder.add(otherNode);
            }
        }

        transportAddresses = nodesBuilder;
        transportAddressesStr = format();
    }

    private String format() {
        StringBuilder stringBuilder = new StringBuilder();
        for (TransportAddress node : transportAddresses) {
            stringBuilder.append(node.getAddress()).append(":").append(node.getPort()).append(";");
        }

        return stringBuilder.toString();
    }

    public String transportAddress() {
        return transportAddressesStr;
    }
}
