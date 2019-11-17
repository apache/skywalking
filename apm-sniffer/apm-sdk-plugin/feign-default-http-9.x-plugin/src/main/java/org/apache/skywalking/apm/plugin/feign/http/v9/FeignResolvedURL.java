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
package org.apache.skywalking.apm.plugin.feign.http.v9;

/**
 * class for {@link PathVarInterceptor} intercept feign url resolved params in url .
 * @author qiyang
 */
public class FeignResolvedURL {
    /**
     * url before resolved
     */
    private String originUrl;
    /**
     * url after resolved
     */
    private String url;

    public FeignResolvedURL(String originUrl) {
        this.originUrl = originUrl;
    }

    public FeignResolvedURL(String originUrl, String url) {
        this.originUrl = originUrl;
        this.url = url;
    }

    public String getOriginUrl() {
        return originUrl;
    }

    public void setOriginUrl(String originUrl) {
        this.originUrl = originUrl;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
