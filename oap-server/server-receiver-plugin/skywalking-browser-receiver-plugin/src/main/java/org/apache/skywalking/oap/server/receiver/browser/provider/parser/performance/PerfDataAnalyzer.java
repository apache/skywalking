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

package org.apache.skywalking.oap.server.receiver.browser.provider.parser.performance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.receiver.browser.provider.parser.performance.decorators.BrowserPerfDecorator;
import org.apache.skywalking.oap.server.receiver.browser.provider.parser.performance.listener.PerfDataAnalysisListener;

@Slf4j
@RequiredArgsConstructor
public class PerfDataAnalyzer {
    private final PerfDataParserListenerManager factory;

    @SuppressWarnings("unchecked")
    public <T extends BrowserPerfDecorator> void doAnalysis(T decorator) {
        if (StringUtil.isBlank(decorator.getService())) {
            return;
        }

        final PerfDataAnalysisListener<T> listener = (PerfDataAnalysisListener<T>) factory.create(decorator.getClass());

        // Use the server side current time.
        long nowMillis = System.currentTimeMillis();
        decorator.setTime(nowMillis);
        if (StringUtil.isBlank(decorator.getServiceVersion())) {
            // Set the default version as latest, considering it is running.
            decorator.setServiceVersion("latest");
        }
        if (StringUtil.isBlank(decorator.getPagePath())) {
            // Set the default page path as root(/).
            decorator.setPagePath("/");
        }

        listener.parse(decorator);
        listener.build();
    }

}
