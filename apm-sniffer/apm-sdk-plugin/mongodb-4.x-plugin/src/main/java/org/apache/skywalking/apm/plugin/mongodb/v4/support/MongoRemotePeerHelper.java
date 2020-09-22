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

package org.apache.skywalking.apm.plugin.mongodb.v4.support;

import com.mongodb.ServerAddress;
import com.mongodb.connection.ServerDescription;
import com.mongodb.internal.connection.Cluster;

@SuppressWarnings("deprecation")
public class MongoRemotePeerHelper {

    private MongoRemotePeerHelper() {
    }

    /**
     * 
     * @param cluster cluster
     * @return result
     */
    public static String getRemotePeer(Cluster cluster) {
        StringBuilder peersBuilder = new StringBuilder();
        for (ServerDescription description : cluster.getDescription().getServerDescriptions()) {
            ServerAddress address = description.getAddress();
            peersBuilder.append(address.getHost()).append(":").append(address.getPort()).append(";");
        }
        return peersBuilder.substring(0, peersBuilder.length() - 1);
    }
}
