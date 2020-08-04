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

package org.apache.skywalking.apm.plugin.feign.http.v9;

import org.apache.skywalking.apm.agent.core.boot.PluginConfig;

public class FeignPluginConfig {
    public static class Plugin {
        @PluginConfig(root = FeignPluginConfig.class)
        public static class Feign {
            /**
             * This config item controls that whether the Feign plugin should collect the parameters of the request.
             */
            public static boolean TRACE_PARAMS = false;

            /**
             * When either {@link Plugin.Feign#TRACE_PARAMS} is enabled, how many characters to keep and send to the OAP
             * backend, use negative values to keep and send the complete parameters, NB. this config item is added for
             * the sake of performance
             */
            public static int FILTER_LENGTH_LIMIT = 1024;
        }
    }
}
