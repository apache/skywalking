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

import org.apache.skywalking.apm.network.common.*;

/**
 * @author peng-yongsheng
 */
public class MetaDataResetCommand extends BaseCommand implements Serializable {

    private KeyStringValuePair.Builder arguments = KeyStringValuePair.newBuilder();

    public MetaDataResetCommand() {
        super("MetaDataReset");
    }

    @Override public Command.Builder serialize() {
        Command.Builder command = Command.newBuilder();
        command.setCommand(getCommand());
        command.addArgs(arguments);
        return command;
    }

    public void specifiedService(int serviceId) {
        arguments.setKey("Specified_Service");
        arguments.setValue(String.valueOf(serviceId));
    }

    public void specifiedInstance(String instanceUUID) {
        arguments.setKey("Specified_Instance");
        arguments.setValue(instanceUUID);
    }

    public void unconditional() {
        arguments.setKey("Unconditional");
        arguments.setValue("");
    }
}
