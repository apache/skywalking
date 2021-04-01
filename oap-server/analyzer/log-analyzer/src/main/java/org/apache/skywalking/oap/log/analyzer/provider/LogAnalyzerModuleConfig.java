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

package org.apache.skywalking.oap.log.analyzer.provider;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.meter.analyzer.prometheus.rule.Rule;
import org.apache.skywalking.oap.meter.analyzer.prometheus.rule.Rules;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;

import static java.util.Objects.nonNull;

@EqualsAndHashCode(callSuper = false)
public class LogAnalyzerModuleConfig extends ModuleConfig {
    @Getter
    @Setter
    private String lalPath = "lal";

    @Getter
    @Setter
    private String malPath = "log-mal-rules";

    @Getter
    @Setter
    private String lalFiles = "default.yaml";

    @Getter
    @Setter
    private String malFiles;

    private List<Rule> meterConfigs;

    public List<String> lalFiles() {
        return Splitter.on(",").omitEmptyStrings().splitToList(Strings.nullToEmpty(getLalFiles()));
    }

    public List<Rule> malConfigs() throws ModuleStartException {
        if (nonNull(meterConfigs)) {
            return meterConfigs;
        }
        final List<String> files = Splitter.on(",")
                                           .omitEmptyStrings()
                                           .splitToList(Strings.nullToEmpty(getMalFiles()));
        meterConfigs = Rules.loadRules(getMalPath(), files);

        return meterConfigs;
    }
}
