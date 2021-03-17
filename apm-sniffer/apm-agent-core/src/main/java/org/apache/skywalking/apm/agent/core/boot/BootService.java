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

package org.apache.skywalking.apm.agent.core.boot;

import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelManager;

/**
 * The <code>BootService</code> is an interface to all remote, which need to boot when plugin mechanism begins to work.
 * {@link #boot()} will be called when <code>BootService</code> start up.
 */
public interface BootService {
    void prepare() throws Throwable;

    void boot() throws Throwable;

    void onComplete() throws Throwable;

    void shutdown() throws Throwable;

    /**
     * @return the shutdown order that {@link ServiceManager} should respect to when shutting down the services, e.g. services depending on {@link GRPCChannelManager} should be shut down after it.
     */
    default int shutdownOrder() {
        return 0;
    }

    /**
     * @return the boot order that {@link ServiceManager} should respect to when starting the services, e.g. services depending on {@link Config.Agent#INSTANCE_NAME} should be started after it.
     */
    default int bootOrder() {
        return 0;
    }
}
