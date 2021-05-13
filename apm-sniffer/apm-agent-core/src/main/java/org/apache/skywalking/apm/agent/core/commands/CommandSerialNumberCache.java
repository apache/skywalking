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

import java.util.Deque;
import java.util.concurrent.LinkedBlockingDeque;

public class CommandSerialNumberCache {
    private static final int DEFAULT_MAX_CAPACITY = 64;
    private final Deque<String> queue;
    private final int maxCapacity;

    public CommandSerialNumberCache() {
        this(DEFAULT_MAX_CAPACITY);
    }

    public CommandSerialNumberCache(int maxCapacity) {
        queue = new LinkedBlockingDeque<String>(maxCapacity);
        this.maxCapacity = maxCapacity;
    }

    public void add(String number) {
        if (queue.size() >= maxCapacity) {
            queue.pollFirst();
        }

        queue.add(number);
    }

    public boolean contain(String command) {
        return queue.contains(command);
    }
}
