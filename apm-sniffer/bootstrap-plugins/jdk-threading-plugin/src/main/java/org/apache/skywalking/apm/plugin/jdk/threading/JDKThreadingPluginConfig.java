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

package org.apache.skywalking.apm.plugin.jdk.threading;

import org.apache.skywalking.apm.agent.core.boot.PluginConfig;

public class JDKThreadingPluginConfig {
    public static class Plugin {
        @PluginConfig(root = JDKThreadingPluginConfig.class)
        public static class JdkThreading {

            /**
             * Threading classes ({@link java.lang.Runnable} and {@link java.util.concurrent.Callable} and their
             * subclasses, including anonymous inner classes) whose name matches any one of the {@code
             * THREADING_CLASS_PREFIXES} (splitted by ,) will be instrumented
             */
            public static String THREADING_CLASS_PREFIXES = "";
        }
    }
}
