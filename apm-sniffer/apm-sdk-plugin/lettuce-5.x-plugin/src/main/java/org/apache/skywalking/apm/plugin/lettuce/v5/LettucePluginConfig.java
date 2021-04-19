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

package org.apache.skywalking.apm.plugin.lettuce.v5;

import org.apache.skywalking.apm.agent.core.boot.PluginConfig;

public class LettucePluginConfig {
    public static class Plugin {
        @PluginConfig(root = LettucePluginConfig.class)
        public static class Lettuce {
            /**
             * If set to true, the parameters of the Redis command would be collected.
             */
            public static boolean TRACE_REDIS_PARAMETERS = false;
            /**
             * For the sake of performance, SkyWalking won't save Redis parameter string into the tag.
             * If TRACE_REDIS_PARAMETERS is set to true, the first {@code REDIS_PARAMETER_MAX_LENGTH} parameter
             * characters would be collected.
             * <p>
             * Set a negative number to save specified length of parameter string to the tag.
             */
            public static int REDIS_PARAMETER_MAX_LENGTH = 128;
        }
    }
}
