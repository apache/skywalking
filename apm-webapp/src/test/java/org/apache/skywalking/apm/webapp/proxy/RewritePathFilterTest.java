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

package org.apache.skywalking.apm.webapp.proxy;

import com.netflix.zuul.context.RequestContext;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.PRE_DECORATION_FILTER_ORDER;

public class RewritePathFilterTest {

    private RewritePathFilter filter = new RewritePathFilter();

    @Before
    public void init() {
        filter.setPath("/graphql");
    }

    @Test
    public void filterOrder() {
        assertThat(filter.filterOrder(), is(PRE_DECORATION_FILTER_ORDER + 2));
    }

    @Test
    public void filterType() {
        assertThat(filter.filterType(), is("pre"));
    }

    @Test
    public void shouldFilter() {
        assertFalse(filter.shouldFilter());
        RequestContext.getCurrentContext().set("requestURI");
        assertTrue(filter.shouldFilter());
    }

    @Test
    public void run() {
        filter.run();
        assertThat(RequestContext.getCurrentContext().get("requestURI"), is("/graphql"));
    }
}