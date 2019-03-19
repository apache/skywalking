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

    private KeyStringValuePair.Builder waitArguments = KeyStringValuePair.newBuilder();
    private KeyStringValuePair.Builder specifiedArguments = KeyStringValuePair.newBuilder();

    public MetaDataResetCommand() {
        super("MetaDataReset");
        waitArguments.setKey("Wait_Seconds");
        waitArguments.setValue(String.valueOf(0));
    }

    @Override public Command.Builder serialize() {
        Command.Builder command = Command.newBuilder();
        command.addArgs(specifiedArguments);
        command.addArgs(waitArguments);
        command.setCommand(getCommand());
        return command;
    }

    public void waitSeconds(int seconds) {
        waitArguments.setValue(String.valueOf(seconds));
    }

    public void specifiedService(int serviceId) {
        specifiedArguments.setKey("Specified_Service");
        specifiedArguments.setValue(String.valueOf(serviceId));
    }

    public void specifiedInstance(String instanceUUID) {
        specifiedArguments.setKey("Specified_Instance");
        specifiedArguments.setValue(instanceUUID);
    }

    public void unconditional() {
        specifiedArguments.setKey("Unconditional");
        specifiedArguments.setValue("");
    }
}
