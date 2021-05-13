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

package org.apache.skywalking.oap.server.cluster.plugin.etcd;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.util.Address;
import org.apache.skywalking.oap.server.library.util.ConnectStringParseException;
import org.apache.skywalking.oap.server.library.util.ConnectUtils;

public class EtcdUtils {

    public EtcdUtils() {
    }

    public static List<URI> parse(ClusterModuleEtcdConfig config) throws ModuleStartException {
        List<URI> uris = new ArrayList<>();
        try {
            List<Address> addressList = ConnectUtils.parse(config.getHostPort());
            for (Address address : addressList) {
                uris.add(URI.create(new StringBuilder("http://").append(address.getHost())
                                                                .append(":")
                                                                .append(address.getPort())
                                                                .toString()));
            }
        } catch (ConnectStringParseException e) {
            throw new ModuleStartException(e.getMessage(), e);
        }

        return uris;
    }
}
