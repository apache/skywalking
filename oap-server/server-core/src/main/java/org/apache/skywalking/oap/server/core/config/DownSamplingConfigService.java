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

package org.apache.skywalking.oap.server.core.config;

import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.library.module.Service;

public class DownSamplingConfigService implements Service {

    private boolean shouldToHour = false;
    private boolean shouldToDay = false;

    public DownSamplingConfigService(List<String> downsampling) {
        downsampling.forEach(value -> {
            if (DownSampling.Hour.getName().toLowerCase().equals(value.toLowerCase())) {
                shouldToHour = true;
            } else if (DownSampling.Day.getName().toLowerCase().equals(value.toLowerCase())) {
                shouldToDay = true;
            }
        });
    }

    public boolean shouldToHour() {
        return shouldToHour;
    }

    public boolean shouldToDay() {
        return shouldToDay;
    }

}
