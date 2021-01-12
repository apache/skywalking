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

package org.apache.skywalking.oap.server.configuration.etcd;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.apache.skywalking.oap.server.library.util.Address;
import org.apache.skywalking.oap.server.library.util.ConnectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * a util for etcd serverAddr parse.
 */
public class EtcdUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(EtcdUtils.class);

    public EtcdUtils() {
    }

    public static List<URI> parse(EtcdServerSettings settings) {
        List<URI> uris = new ArrayList<>();
        try {
            LOGGER.info("etcd settings is {}", settings);
            List<Address> addressList = ConnectUtils.parse(settings.getServerAddr());
            for (Address address : addressList) {
                uris.add(new URI("http", null, address.getHost(), address.getPort(), null, null, null));
            }
        } catch (Exception e) {
            throw new EtcdConfigException(e.getMessage(), e);
        }

        return uris;
    }

    public static List<URI> parseProp(Properties properties) {
        List<URI> uris = new ArrayList<>();
        try {
            LOGGER.info("etcd server addr is {}", properties);
            List<Address> addressList = ConnectUtils.parse(properties.getProperty("serverAddr"));
            for (Address address : addressList) {
                uris.add(new URI("http", null, address.getHost(), address.getPort(), null, null, null));
            }
        } catch (Exception e) {
            throw new EtcdConfigException(e.getMessage(), e);
        }

        return uris;
    }

}
