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
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.oap.server.core.oal.rt;

import lombok.Getter;
import lombok.ToString;
import org.apache.skywalking.oap.server.core.Const;

import static java.util.Objects.requireNonNull;

/**
 * Define multiple OAL configuration
 */
@Getter
@ToString
public abstract class OALDefine {

    OALDefine(final String configFile,
              final String sourcePackage,
              final String dynamicMetricsClassPackage,
              final String dynamicMetricsBuilderClassPackage, final String dynamicDispatcherClassPackage) {
        this.configFile = requireNonNull(configFile);
        this.sourcePackage = appendPoint(requireNonNull(sourcePackage));
        this.dynamicMetricsClassPackage = appendPoint(requireNonNull(dynamicMetricsClassPackage));
        this.dynamicMetricsBuilderClassPackage = appendPoint(requireNonNull(dynamicMetricsBuilderClassPackage));
        this.dynamicDispatcherClassPackage = appendPoint(requireNonNull(dynamicDispatcherClassPackage));
    }

    private final String configFile;
    private final String sourcePackage;
    private final String dynamicMetricsClassPackage;
    private final String dynamicMetricsBuilderClassPackage;
    private final String dynamicDispatcherClassPackage;

    private String appendPoint(String classPackage) {
        if (classPackage.endsWith(Const.POINT)) {
            return classPackage;
        }
        return classPackage + Const.POINT;
    }
}
