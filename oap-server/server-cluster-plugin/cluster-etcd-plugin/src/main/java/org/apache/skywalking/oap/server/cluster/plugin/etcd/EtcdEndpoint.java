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

import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;

/**
 * an instance json to register to etcd.
 */
public class EtcdEndpoint implements Serializable {

    @Setter
    @Getter
    private String serviceName;

    @Setter
    @Getter
    private String host;

    @Setter
    @Getter
    private int port;

    public EtcdEndpoint(Builder builder) {
        setServiceName(builder.serviceName);
        setHost(builder.host);
        setPort(builder.port);
    }

    public static class Builder {
        private String serviceName;

        private String host;

        private int port;

        public Builder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public EtcdEndpoint build() {
            return new EtcdEndpoint(this);
        }
    }
}
