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

package org.apache.skywalking.e2e.metrics;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.apache.skywalking.e2e.AbstractQuery;

@Data
@Accessors(fluent = true)
@EqualsAndHashCode(callSuper = true)
public class BrowserMetricsQuery extends AbstractQuery<BrowserMetricsQuery> {
    public static final String BROWSER_APP_PV = "browser_app_pv";
    public static final String BROWSER_APP_ERROR_RATE = "browser_app_error_rate";
    public static final String BROWSER_APP_ERROR_SUM = "browser_app_error_sum";

    public static final String[] ALL_BROWSER_METRICS = {
        BROWSER_APP_PV,
        BROWSER_APP_ERROR_SUM,
        BROWSER_APP_ERROR_RATE
    };

    public static final String BROWSER_APP_SINGLE_VERSION_PV = "browser_app_single_version_pv";
    public static final String BROWSER_APP_SINGLE_VERSION_ERROR_RATE = "browser_app_single_version_error_rate";
    public static final String BROWSER_APP_SINGLE_VERSION_ERROR_SUM = "browser_app_single_version_error_sum";

    public static final String[] ALL_BROWSER_SINGLE_VERSION_METRICS = {
        BROWSER_APP_SINGLE_VERSION_PV,
        BROWSER_APP_SINGLE_VERSION_ERROR_RATE,
        BROWSER_APP_SINGLE_VERSION_ERROR_SUM
    };

    public static final String BROWSER_APP_PAGE_PV = "browser_app_page_pv";
    public static final String BROWSER_APP_PAGE_ERROR_RATE = "browser_app_page_error_rate";
    public static final String BROWSER_APP_PAGE_ERROR_SUM = "browser_app_page_error_sum";

    public static final String BROWSER_APP_PAGE_AJAX_ERROR_SUM = "browser_app_page_ajax_error_sum";
    public static final String BROWSER_APP_PAGE_RESOURCE_ERROR_SUM = "browser_app_page_resource_error_sum";
    public static final String BROWSER_APP_PAGE_JS_ERROR_SUM = "browser_app_page_js_error_sum";
    public static final String BROWSER_APP_PAGE_UNKNOWN_ERROR_SUM = "browser_app_page_unknown_error_sum";

    public static final String BROWSER_APP_PAGE_REDIRECT_AVG = "browser_app_page_redirect_avg";
    public static final String BROWSER_APP_PAGE_DNS_AVG = "browser_app_page_dns_avg";
    public static final String BROWSER_APP_PAGE_TTFB_AVG = "browser_app_page_ttfb_avg";
    public static final String BROWSER_APP_PAGE_TCP_AVG = "browser_app_page_tcp_avg";
    public static final String BROWSER_APP_PAGE_TRANS_AVG = "browser_app_page_trans_avg";
    public static final String BROWSER_APP_PAGE_DOM_ANALYSIS_AVG = "browser_app_page_dom_analysis_avg";
    public static final String BROWSER_APP_PAGE_FPT_AVG = "browser_app_page_fpt_avg";
    public static final String BROWSER_APP_PAGE_DOM_READY_AVG = "browser_app_page_dom_ready_avg";
    public static final String BROWSER_APP_PAGE_LOAD_PAGE_AVG = "browser_app_page_load_page_avg";
    public static final String BROWSER_APP_PAGE_RES_AVG = "browser_app_page_res_avg";
    public static final String BROWSER_APP_PAGE_SSL_AVG = "browser_app_page_ssl_avg";
    public static final String BROWSER_APP_PAGE_TTL_AVG = "browser_app_page_ttl_avg";
    public static final String BROWSER_APP_PAGE_FIRST_PACK_AVG = "browser_app_page_first_pack_avg";
    public static final String BROWSER_APP_PAGE_FMP_AVG = "browser_app_page_fmp_avg";

    public static final String[] ALL_BROWSER_PAGE_METRICS = {
        BROWSER_APP_PAGE_PV,
        BROWSER_APP_PAGE_ERROR_RATE,
        BROWSER_APP_PAGE_ERROR_SUM,
        BROWSER_APP_PAGE_AJAX_ERROR_SUM,
        BROWSER_APP_PAGE_RESOURCE_ERROR_SUM,
        BROWSER_APP_PAGE_JS_ERROR_SUM,
        BROWSER_APP_PAGE_UNKNOWN_ERROR_SUM,
        BROWSER_APP_PAGE_REDIRECT_AVG,
        BROWSER_APP_PAGE_DNS_AVG,
        BROWSER_APP_PAGE_TTFB_AVG,
        BROWSER_APP_PAGE_TCP_AVG,
        BROWSER_APP_PAGE_TRANS_AVG,
        BROWSER_APP_PAGE_DOM_ANALYSIS_AVG,
        BROWSER_APP_PAGE_FPT_AVG,
        BROWSER_APP_PAGE_DOM_READY_AVG,
        BROWSER_APP_PAGE_LOAD_PAGE_AVG,
        BROWSER_APP_PAGE_RES_AVG,
        BROWSER_APP_PAGE_SSL_AVG,
        BROWSER_APP_PAGE_TTL_AVG,
        BROWSER_APP_PAGE_FIRST_PACK_AVG,
        BROWSER_APP_PAGE_FMP_AVG
    };

    public static final String BROWSER_APP_PAGE_FPT_PERCENTILE = "browser_app_page_fpt_percentile";
    public static final String BROWSER_APP_PAGE_DOM_READY_PERCENTILE = "browser_app_page_dom_ready_percentile";
    public static final String BROWSER_APP_PAGE_LOAD_PAGE_PERCENTILE = "browser_app_page_load_page_percentile";
    public static final String BROWSER_APP_PAGE_TTL_PERCENTILE = "browser_app_page_ttl_percentile";
    public static final String BROWSER_APP_PAGE_FIRST_PACK_PERCENTILE = "browser_app_page_first_pack_percentile";
    public static final String BROWSER_APP_PAGE_FMP_PERCENTILE = "browser_app_page_fmp_percentile";

    public static final String[] ALL_BROWSER_PAGE_MULTIPLE_LINEAR_METRICS = {
        BROWSER_APP_PAGE_FPT_PERCENTILE,
        BROWSER_APP_PAGE_TTL_PERCENTILE,
        BROWSER_APP_PAGE_DOM_READY_PERCENTILE,
        BROWSER_APP_PAGE_LOAD_PAGE_PERCENTILE,
        BROWSER_APP_PAGE_FIRST_PACK_PERCENTILE,
        BROWSER_APP_PAGE_FMP_PERCENTILE
    };
}
