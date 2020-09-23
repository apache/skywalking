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

import org.apache.skywalking.apm.agent.core.boot.PluginConfig;

public class MongoPluginConfig {
    public static class Plugin {
        @PluginConfig(root = MongoPluginConfig.class)
        public static class MongoDB {
            /**
             * If true, trace all the parameters in MongoDB access, default is false. Only trace the operation, not
             * include parameters.
             */
            public static boolean TRACE_PARAM = false;

            /**
             * For the sake of performance, SkyWalking won't save the entire parameters string into the tag, but only
             * the first {@code FILTER_LENGTH_LIMIT} characters.
             * <p>
             * Set a negative number to save the complete parameter string to the tag.
             */
            public static int FILTER_LENGTH_LIMIT = 256;
        }
    }
}
