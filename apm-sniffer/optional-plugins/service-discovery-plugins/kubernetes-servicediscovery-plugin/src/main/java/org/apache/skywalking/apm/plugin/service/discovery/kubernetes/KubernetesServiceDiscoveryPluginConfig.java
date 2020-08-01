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

package org.apache.skywalking.apm.plugin.service.discovery.kubernetes;

import org.apache.skywalking.apm.agent.core.boot.PluginConfig;

public class KubernetesServiceDiscoveryPluginConfig {
    public static class Plugin {
        @PluginConfig(root = KubernetesServiceDiscoveryPluginConfig.class)
        public static class KubernetesService {
            /**
             * which namespace is the receiver running
             */
            public static String NAMESPACE = "default";
            /**
             * oap receiver kubernetes service label selector
             */
            public static String LABEL_SELECTOR = "";
            /**
             * oap receiver grpc port name define in kubernetes service
             */
            public static String PORT_NAME = "";
        }
    }
}
