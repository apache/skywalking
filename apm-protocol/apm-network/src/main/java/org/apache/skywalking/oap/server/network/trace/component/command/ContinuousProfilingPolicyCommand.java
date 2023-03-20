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

package org.apache.skywalking.oap.server.network.trace.component.command;

import com.google.gson.Gson;
import org.apache.skywalking.apm.network.common.v3.Command;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;

import java.util.List;

public class ContinuousProfilingPolicyCommand extends BaseCommand implements Serializable {
    public static final String NAME = "ContinuousProfilingPolicyQuery";
    private static final Gson GSON = new Gson();

    private List<ContinuousProfilingPolicy> policies;

    public ContinuousProfilingPolicyCommand(String serialNumber, List<ContinuousProfilingPolicy> policies) {
        super(NAME, serialNumber);
        this.policies = policies;
    }

    @Override
    public Command.Builder serialize() {
        final Command.Builder builder = commandBuilder();
        builder.addArgs(KeyStringValuePair.newBuilder()
            .setKey("ServiceWithPolicyJSON").setValue(GSON.toJson(policies)).build());
        return builder;
    }
}