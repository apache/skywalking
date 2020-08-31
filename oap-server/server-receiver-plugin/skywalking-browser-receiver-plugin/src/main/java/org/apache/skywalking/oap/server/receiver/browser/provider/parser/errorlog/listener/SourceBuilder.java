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

package org.apache.skywalking.oap.server.receiver.browser.provider.parser.errorlog.listener;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.skywalking.apm.network.language.agent.v3.ErrorCategory;
import org.apache.skywalking.oap.server.core.browser.source.BrowserAppPageTraffic;
import org.apache.skywalking.oap.server.core.browser.source.BrowserAppSingleVersionTraffic;
import org.apache.skywalking.oap.server.core.browser.source.BrowserAppTraffic;
import org.apache.skywalking.oap.server.core.browser.source.BrowserAppTrafficCategory;
import org.apache.skywalking.oap.server.core.browser.source.BrowserAppTrafficSource;
import org.apache.skywalking.oap.server.core.browser.source.BrowserErrorCategory;
import org.apache.skywalking.oap.server.core.config.NamingControl;

@RequiredArgsConstructor
class SourceBuilder {
    private final NamingControl namingControl;

    @Getter
    private String service;

    public void setService(final String service) {
        this.service = namingControl.formatServiceName(service);
    }

    @Getter
    private String serviceVersion;

    public void setServiceVersion(final String serviceVersion) {
        this.serviceVersion = namingControl.formatInstanceName(serviceVersion);
    }

    @Getter
    private String patePath;

    public void setPatePath(final String patePath) {
        this.patePath = namingControl.formatEndpointName(service, patePath);
    }

    @Setter
    @Getter
    private long timeBucket;

    @Setter
    @Getter
    private BrowserAppTrafficCategory trafficCategory;

    @Setter
    @Getter
    private BrowserErrorCategory errorCategory;

    public void setErrorCategory(ErrorCategory category) {
        this.errorCategory = BrowserErrorCategory.fromErrorCategory(category);
    }

    private void toBrowserAppTrafficSource(BrowserAppTrafficSource source) {
        source.setTimeBucket(timeBucket);
        source.setTrafficCategory(trafficCategory);
        source.setErrorCategory(errorCategory);
    }

    /**
     * Browser service traffic error related source.
     */
    BrowserAppTraffic toBrowserAppTraffic() {
        BrowserAppTraffic traffic = new BrowserAppTraffic();
        toBrowserAppTrafficSource(traffic);
        traffic.setName(service);
        traffic.setTrafficCategory(trafficCategory);
        traffic.setErrorCategory(errorCategory);
        return traffic;
    }

    /**
     * Browser single version error metrics related source.
     */
    BrowserAppSingleVersionTraffic toBrowserAppSingleVersionTraffic() {
        BrowserAppSingleVersionTraffic traffic = new BrowserAppSingleVersionTraffic();
        toBrowserAppTrafficSource(traffic);
        traffic.setName(serviceVersion);
        traffic.setServiceName(service);
        return traffic;
    }

    /**
     * Browser page error metrics related source.
     */
    BrowserAppPageTraffic toBrowserAppPageTraffic() {
        BrowserAppPageTraffic traffic = new BrowserAppPageTraffic();
        toBrowserAppTrafficSource(traffic);
        traffic.setName(patePath);
        traffic.setServiceName(service);
        return traffic;
    }
}
