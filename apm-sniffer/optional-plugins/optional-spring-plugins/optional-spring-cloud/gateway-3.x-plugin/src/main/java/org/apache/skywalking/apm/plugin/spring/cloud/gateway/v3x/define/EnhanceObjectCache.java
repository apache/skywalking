/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.skywalking.apm.plugin.spring.cloud.gateway.v3x.define;

import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;

/**
 * enhance object cache
 */
public class EnhanceObjectCache {

    private String url;
    private AbstractSpan span;
    private AbstractSpan span1;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setCacheSpan(AbstractSpan span) {
        this.span = span;
    }

    public AbstractSpan getSpan() {
        return span;
    }

    public AbstractSpan getSpan1() {
        return span1;
    }

    public void setSpan1(final AbstractSpan span) {
        span1 = span;
    }
}
