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

package org.apache.skywalking.apm.network.trace.component.command;

import org.apache.skywalking.apm.network.common.Command;
import org.apache.skywalking.apm.network.common.KeyStringValuePair;

import java.util.List;

/**
 * Clear the service metadata cache and other metadata caches belong to it, and re-register them.
 *
 * @author peng-yongsheng
 */
public class ServiceResetCommand extends BaseCommand implements Serializable, Deserializable<ServiceResetCommand> {
    public static final Deserializable<ServiceResetCommand> DESERIALIZER = new ServiceResetCommand("");
    public static final String NAME = "ServiceMetadataReset";

    public ServiceResetCommand(String serialNumber) {
        super(NAME, serialNumber);
    }

    @Override
    public Command.Builder serialize() {
        return commandBuilder();
    }

    @Override
    public ServiceResetCommand deserialize(Command command) {
        final List<KeyStringValuePair> argsList = command.getArgsList();
        String serialNumber = null;
        for (final KeyStringValuePair pair : argsList) {
            if ("SerialNumber".equals(pair.getKey())) {
                serialNumber = pair.getValue();
                break;
            }
        }
        return new ServiceResetCommand(serialNumber);
    }
}
