/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.skywalking.apm.agent.core.commands;

import org.apache.skywalking.apm.network.common.v3.Command;

/**
 * Indicates that the execution of a command failed
 */
public class CommandExecutionException extends Throwable {
    private final Command command;

    /**
     * Constructs a new {@code ExecuteFailedException} with null detail message and the command whose execution failed
     *
     * @param command the command whose execution failed
     */
    public CommandExecutionException(final Command command) {
        this(null, command);
    }

    /**
     * Constructs a new {@code ExecuteFailedException} with given detail message and the command whose execution failed
     *
     * @param message the detail message of the exception
     * @param command the command whose execution failed
     */
    public CommandExecutionException(final String message, final Command command) {
        super(message);
        this.command = command;
    }

    public Command command() {
        return command;
    }
}
