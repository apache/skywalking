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

package org.apache.skywalking.apm.agent.core.plugin;

import java.io.File;
import java.io.IOException;
import net.bytebuddy.dynamic.DynamicType;
import org.apache.skywalking.apm.agent.core.boot.AgentPackageNotFoundException;
import org.apache.skywalking.apm.agent.core.boot.AgentPackagePath;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;

/**
 * The manipulated class output. Write the dynamic classes to the `debugging` folder, when we need to do some debug and
 * recheck.
 */
public enum InstrumentDebuggingClass {
    INSTANCE;

    private static final ILog LOGGER = LogManager.getLogger(InstrumentDebuggingClass.class);
    private File debuggingClassesRootPath;

    public void log(DynamicType dynamicType) {
        if (!Config.Agent.IS_OPEN_DEBUGGING_CLASS) {
            return;
        }

        /**
         * try to do I/O things in synchronized way, to avoid unexpected situations.
         */
        synchronized (INSTANCE) {
            try {
                if (debuggingClassesRootPath == null) {
                    try {
                        debuggingClassesRootPath = new File(AgentPackagePath.getPath(), "/debugging");
                        if (!debuggingClassesRootPath.exists()) {
                            debuggingClassesRootPath.mkdir();
                        }
                    } catch (AgentPackageNotFoundException e) {
                        LOGGER.error(e, "Can't find the root path for creating /debugging folder.");
                    }
                }

                try {
                    dynamicType.saveIn(debuggingClassesRootPath);
                } catch (IOException e) {
                    LOGGER.error(e, "Can't save class {} to file." + dynamicType.getTypeDescription().getActualName());
                }
            } catch (Throwable t) {
                LOGGER.error(t, "Save debugging classes fail.");
            }
        }
    }
}
