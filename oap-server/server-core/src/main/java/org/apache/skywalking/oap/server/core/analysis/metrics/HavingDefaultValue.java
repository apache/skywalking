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

package org.apache.skywalking.oap.server.core.analysis.metrics;

/**
 * HavingDefaultValue interface defines a capability for the metric implementations, which has the declaration of the
 * default value.
 *
 * For the declared metrics, the OAP server would skip the persistence of minute dimensionality metrics to reduce
 * resource costs for the database, when the value of the metric is the default.
 */
public interface HavingDefaultValue {
    /**
     * @return true if the implementation of this metric has the definition of default value.
     */
    default boolean haveDefault() {
        return false;
    }

    /**
     * @return true when the latest value equals the default value.
     */
    default boolean isDefaultValue() {
        return false;
    }
}
