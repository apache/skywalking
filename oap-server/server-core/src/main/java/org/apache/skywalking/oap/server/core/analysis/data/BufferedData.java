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

package org.apache.skywalking.oap.server.core.analysis.data;

import java.util.List;

/**
 * BufferedData represents a data collection in the memory. Data could be accepted and be drain to other collection.
 *
 * {@link #accept(Object)} and {@link #read()} wouldn't be required to thread-safe. BufferedData usually hosts by {@link
 * ReadWriteSafeCache}.
 */
public interface BufferedData<T> {
    /**
     * Accept the data into the cache if it fits the conditions. The implementation maybe wouldn't accept the new input
     * data.
     *
     * @param data to be added potentially.
     */
    void accept(T data);

    /**
     * Read all existing buffered data, and clear the memory.
     */
    List<T> read();
}
