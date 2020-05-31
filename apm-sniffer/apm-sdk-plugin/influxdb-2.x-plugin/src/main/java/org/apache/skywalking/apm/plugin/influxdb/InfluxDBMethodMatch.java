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

package org.apache.skywalking.apm.plugin.influxdb;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;

public enum InfluxDBMethodMatch {
    INSTANCE;

    public ElementMatcher.Junction<MethodDescription> getInfluxDBMethodMatcher() {
        return named("setLogLevel").or(named("enableGzip"))
                              .or(named("disableGzip"))
                              .or(named("enableBatch"))
                              .or(named("disableBatch"))
                              .or(named("ping"))
                              .or(named("write"))
                              .or(named("query"))
                              .or(named("createDatabase"))
                              .or(named("deleteDatabase"))
                              .or(named("describeDatabases"))
                              .or(named("flush"))
                              .or(named("setConsistency"))
                              .or(named("setDatabase"))
                              .or(named("setRetentionPolicy"))
                              .or(named("createRetentionPolicy"))
                              .or(named("dropRetentionPolicy"));
    }

}
