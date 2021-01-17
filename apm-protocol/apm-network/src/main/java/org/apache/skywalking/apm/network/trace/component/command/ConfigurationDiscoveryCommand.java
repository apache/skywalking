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

import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.apm.network.common.v3.Command;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;

public class ConfigurationDiscoveryCommand extends BaseCommand implements Serializable, Deserializable<ConfigurationDiscoveryCommand> {
    public static final Deserializable<ConfigurationDiscoveryCommand> DESERIALIZER = new ConfigurationDiscoveryCommand(
        "", "", "", new ArrayList<>());
    public static final String NAME = ConfigurationDiscoveryCommand.class.getSimpleName();

    public static final String UUID_CONST_NAME = "UUID";
    public static final String SERIAL_NUMBER_CONST_NAME = "SerialNumber";
    public static final String SERVICE_CONST_NAME = "SERVICE";

    /*
     * If config is unchanged, then could response the same uuid, and config is not required.
     */
    private String uuid;
    /*
     * Current service name.
     */
    private String service;
    /*
     * The configuration of service.
     */
    private List<KeyStringValuePair> config;

    public ConfigurationDiscoveryCommand(String serialNumber,
                                         String service,
                                         String uuid,
                                         List<KeyStringValuePair> config) {
        super(NAME, serialNumber);
        this.uuid = uuid;
        this.service = service;
        this.config = config;
    }

    @Override
    public ConfigurationDiscoveryCommand deserialize(Command command) {
        String serialNumber = null;
        String uuid = null;
        String serviceName = null;
        List<KeyStringValuePair> config = new ArrayList<>();

        for (final KeyStringValuePair pair : command.getArgsList()) {
            if (SERIAL_NUMBER_CONST_NAME.equals(pair.getKey())) {
                serialNumber = pair.getValue();
            } else if (UUID_CONST_NAME.equals(pair.getKey())) {
                uuid = pair.getValue();
            } else if (SERVICE_CONST_NAME.equals(pair.getKey())) {
                serviceName = pair.getValue();
            } else {
                //add config item to config list.
                config.add(pair);
            }
        }
        return new ConfigurationDiscoveryCommand(serialNumber, serviceName, uuid, config);
    }

    @Override
    public Command.Builder serialize() {
        final Command.Builder builder = commandBuilder();
        builder.addArgs(KeyStringValuePair.newBuilder().setKey(SERVICE_CONST_NAME).setValue(service));
        builder.addArgs(KeyStringValuePair.newBuilder().setKey(UUID_CONST_NAME).setValue(uuid));
        builder.addAllArgs(config);
        return builder;
    }

    public String getUuid() {
        return uuid;
    }

    public String getService() {
        return service;
    }

    public List<KeyStringValuePair> getConfig() {
        return config;
    }

    @Override
    public String toString() {
        return "ConfigurationDiscoveryCommand{" +
            "uuid='" + uuid + '\'' +
            ", service='" + service + '\'' +
            ", config=" + config +
            '}';
    }
}
