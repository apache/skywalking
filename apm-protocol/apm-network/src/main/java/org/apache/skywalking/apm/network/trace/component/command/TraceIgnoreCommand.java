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

/**
 * Trace ignore sync, each configuration downstream is the full amount of data related to the received agent.
 */
public class TraceIgnoreCommand extends BaseCommand implements Serializable {

    public TraceIgnoreCommand(String serialNumber) {
        super("TraceIgnore", serialNumber);
    }

    @Override
    public Command.Builder serialize() {
        return commandBuilder();
    }

    public void addRule(String path) {
        KeyStringValuePair.Builder arguments = KeyStringValuePair.newBuilder();
        arguments.setKey("Path");
        arguments.setValue(path);
        commandBuilder().addArgs(arguments);
    }
}
