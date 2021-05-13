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

import org.apache.skywalking.apm.network.common.v3.Command;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;

public abstract class BaseCommand {

    private final String command;
    private final String serialNumber;
    private final Command.Builder commandBuilder;

    BaseCommand(String command, String serialNumber) {
        this.command = command;
        this.serialNumber = serialNumber;
        this.commandBuilder = Command.newBuilder();

        KeyStringValuePair.Builder arguments = KeyStringValuePair.newBuilder();
        arguments.setKey("SerialNumber");
        arguments.setValue(serialNumber);

        this.commandBuilder.setCommand(command);
        this.commandBuilder.addArgs(arguments);
    }

    Command.Builder commandBuilder() {
        return commandBuilder;
    }

    public String getCommand() {
        return command;
    }

    public String getSerialNumber() {
        return serialNumber;
    }
}
