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

package org.apache.skywalking.oap.server.library.util.genai;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * GenAI provider and model pricing configuration.
 * Shared by agent-based GenAI analysis and OTLP-based AI Gateway monitoring.
 */
public class GenAIPricingConfig {

    @Getter
    @Setter
    private List<Provider> providers = new ArrayList<>();

    @Getter
    @Setter
    public static class Provider {
        private String provider;
        private List<String> prefixMatch = new ArrayList<>();
        private List<Model> models = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class Model {
        private String name;
        private List<String> aliases = new ArrayList<>();
        private double inputEstimatedCostPerM;
        private double outputEstimatedCostPerM;
    }
}
