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

package org.apache.skywalking.oap.server.core.command;

import java.util.UUID;
import org.apache.skywalking.apm.network.trace.component.command.ProfileTaskCommand;
import org.apache.skywalking.oap.server.core.query.type.ProfileTask;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;

/**
 * CommandService represents the command creation factory. All commands for downstream agents should be created here.
 */
public class CommandService implements Service {
    private final ModuleManager moduleManager;

    public CommandService(final ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    public ProfileTaskCommand newProfileTaskCommand(ProfileTask task) {
        final String serialNumber = UUID.randomUUID().toString();
        return new ProfileTaskCommand(
            serialNumber, task.getId(), task.getEndpointName(), task.getDuration(), task.getMinDurationThreshold(), task
            .getDumpPeriod(), task.getMaxSamplingCount(), task.getStartTime(), task.getCreateTime());
    }

    private String generateSerialNumber(final int serviceInstanceId, final long time,
                                        final String serviceInstanceUUID) {
        return UUID.randomUUID().toString(); // Simply generate a uuid without taking care of the parameters
    }
}
