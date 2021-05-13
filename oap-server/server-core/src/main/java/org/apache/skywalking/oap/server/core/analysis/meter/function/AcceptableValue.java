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

package org.apache.skywalking.oap.server.core.analysis.meter.function;

import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;

/**
 * Indicate this function accepting the data of type T.
 */
public interface AcceptableValue<T> {
    void accept(MeterEntity entity, T value);

    /**
     * @return a new instance based on the implementation, it should be the same class.
     */
    AcceptableValue<T> createNew();

    /**
     * @return builder
     */
    Class<? extends StorageHashMapBuilder> builder();

    void setTimeBucket(long timeBucket);

    long getTimeBucket();
}
