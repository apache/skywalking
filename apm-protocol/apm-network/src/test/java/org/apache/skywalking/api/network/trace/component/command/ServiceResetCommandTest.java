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

package org.apache.skywalking.api.network.trace.component.command;

import org.apache.skywalking.apm.network.common.Command;
import org.apache.skywalking.apm.network.common.KeyStringValuePair;
import org.apache.skywalking.apm.network.trace.component.command.ServiceResetCommand;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author jsbxyyx
 */
public class ServiceResetCommandTest {

    @Test
    public void serialize_Serialize_SerialNumberIsaa() throws Exception {
        ServiceResetCommand command = new ServiceResetCommand("aa");
        Command.Builder builder = command.serialize();

        Assert.assertEquals(ServiceResetCommand.NAME, builder.getCommand());
        Assert.assertEquals("aa", builder.getArgs(0).getValue());
    }

    @Test
    public void deserialize_NullPointerException_CommandKeyIsNotSerialNumber() throws Exception {
        ServiceResetCommand command = new ServiceResetCommand("aa");

        Command command1 = Command.newBuilder()
                .addArgs(KeyStringValuePair.newBuilder().setKey("aa").setValue("aa").build())
                .build();
        boolean exception = false;
        try {
            command.deserialize(command1);
        } catch (NullPointerException e) {
            exception = true;
        }
        Assert.assertEquals(true, exception);
    }

    @Test
    public void deserialize_SerialNumberCompare_CommandKeyIsSerialNumberValueIsaa() throws Exception {
        ServiceResetCommand command = new ServiceResetCommand("aa");

        Command command2 = Command.newBuilder()
                .addArgs(KeyStringValuePair.newBuilder().setKey("SerialNumber").setValue("aa").build())
                .build();
        ServiceResetCommand deserialize2 = command.deserialize(command2);
        Assert.assertEquals("aa", deserialize2.getSerialNumber());
    }

    @Test
    public void deserialize_SerializeAndDeserialize_SerialNumberValueIsaa() throws Exception {
        ServiceResetCommand command = new ServiceResetCommand("aa");
        Command.Builder builder = command.serialize();
        Command command3 = builder.build();
        ServiceResetCommand deserialize3 = command.deserialize(command3);
        Assert.assertEquals(command.getCommand(), deserialize3.getCommand());
        Assert.assertEquals(command.getSerialNumber(), deserialize3.getSerialNumber());
    }

}
