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

package org.apache.skywalking.oap.server.fetcher.prometheus.provider;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Getter;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

@Getter
public class PrometheusFetcherConfig extends ModuleConfig {

    private int maxConvertWorker;

    private String enabledRules;

    private final String rulePath = "fetcher-prom-rules";

    List<String> getEnabledRules() {
        return Arrays.stream(Optional.ofNullable(enabledRules).orElse("").split(","))
                     .map(String::trim)
                     .filter(StringUtil::isNotEmpty)
                     .collect(Collectors.toList());
    }

    public int getMaxConvertWorker() {
        return maxConvertWorker <= 0 ? Math.max(1, Runtime.getRuntime().availableProcessors() / 2) : maxConvertWorker;
    }
}
