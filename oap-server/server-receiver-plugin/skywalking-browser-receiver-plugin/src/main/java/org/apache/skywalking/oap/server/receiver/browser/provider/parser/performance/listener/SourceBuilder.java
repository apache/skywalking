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

package org.apache.skywalking.oap.server.receiver.browser.provider.parser.performance.listener;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.browser.source.BrowserAppPagePerf;
import org.apache.skywalking.oap.server.core.browser.source.BrowserAppPageTraffic;
import org.apache.skywalking.oap.server.core.browser.source.BrowserAppPerf;
import org.apache.skywalking.oap.server.core.browser.source.BrowserAppPerfSource;
import org.apache.skywalking.oap.server.core.browser.source.BrowserAppSingleVersionPerf;
import org.apache.skywalking.oap.server.core.browser.source.BrowserAppSingleVersionTraffic;
import org.apache.skywalking.oap.server.core.browser.source.BrowserAppTraffic;
import org.apache.skywalking.oap.server.core.browser.source.BrowserAppTrafficCategory;
import org.apache.skywalking.oap.server.core.browser.source.BrowserAppTrafficSource;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.Source;

/**
 * Browser traffic and performance related source.
 */
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

    // performance data detail
    @Setter
    @Getter
    private int redirectTime;
    @Setter
    @Getter
    private int dnsTime;
    @Setter
    @Getter
    private int ttfbTime;
    @Setter
    @Getter
    private int tcpTime;
    @Setter
    @Getter
    private int transTime;
    @Setter
    @Getter
    private int domAnalysisTime;
    @Setter
    @Getter
    private int fptTime;
    @Setter
    @Getter
    private int domReadyTime;
    @Setter
    @Getter
    private int loadPageTime;
    @Setter
    @Getter
    private int resTime;
    @Setter
    @Getter
    private int sslTime;
    @Setter
    @Getter
    private int ttlTime;
    @Setter
    @Getter
    private int firstPackTime;
    @Setter
    @Getter
    private int fmpTime;

    private void toSource(Source source) {
        source.setTimeBucket(timeBucket);
    }

    private void toBrowserAppTrafficSource(BrowserAppTrafficSource source) {
        toSource(source);
        source.setTrafficCategory(BrowserAppTrafficCategory.NORMAL);
    }

    /**
     * Browser service meta and traffic metrics related source.
     */
    BrowserAppTraffic toBrowserAppTraffic() {
        BrowserAppTraffic traffic = new BrowserAppTraffic();
        traffic.setName(service);
        toBrowserAppTrafficSource(traffic);
        return traffic;
    }

    /**
     * Browser single version meta and traffic metrics related source.
     */
    BrowserAppSingleVersionTraffic toBrowserAppSingleVersionTraffic() {
        BrowserAppSingleVersionTraffic traffic = new BrowserAppSingleVersionTraffic();
        traffic.setName(serviceVersion);
        traffic.setServiceName(service);
        toBrowserAppTrafficSource(traffic);
        return traffic;
    }

    /**
     * Browser page meta and traffic metrics related source.
     */
    BrowserAppPageTraffic toBrowserAppPageTraffic() {
        BrowserAppPageTraffic traffic = new BrowserAppPageTraffic();
        traffic.setName(patePath);
        traffic.setServiceName(service);
        toBrowserAppTrafficSource(traffic);
        return traffic;
    }

    private void toBrowserAppPerfSource(BrowserAppPerfSource source) {
        toSource(source);
        source.setRedirectTime(redirectTime);
        source.setDnsTime(dnsTime);
        source.setTtfbTime(ttfbTime);
        source.setTcpTime(tcpTime);
        source.setTransTime(transTime);
        source.setDomAnalysisTime(domAnalysisTime);
        source.setFptTime(fptTime);
        source.setDomReadyTime(domReadyTime);
        source.setLoadPageTime(loadPageTime);
        source.setResTime(resTime);
        source.setSslTime(sslTime);
        source.setTtlTime(ttlTime);
        source.setFirstPackTime(firstPackTime);
        source.setFmpTime(fmpTime);
    }

    /**
     * Browser service performance related source.
     */
    BrowserAppPerf toBrowserAppPerf() {
        BrowserAppPerf perf = new BrowserAppPerf();
        perf.setName(service);
        toBrowserAppPerfSource(perf);
        return perf;
    }

    /**
     * Browser single version performance related source.
     */
    BrowserAppSingleVersionPerf toBrowserAppSingleVersionPerf() {
        BrowserAppSingleVersionPerf perf = new BrowserAppSingleVersionPerf();
        perf.setName(serviceVersion);
        perf.setServiceName(service);
        toBrowserAppPerfSource(perf);
        return perf;
    }

    /**
     * Browser page performance related source.
     */
    BrowserAppPagePerf toBrowserAppPagePerf() {
        BrowserAppPagePerf perf = new BrowserAppPagePerf();
        perf.setName(patePath);
        perf.setServiceName(service);
        toBrowserAppPerfSource(perf);
        return perf;
    }
}
