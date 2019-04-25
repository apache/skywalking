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

package org.apache.skywalking.apm.webapp.security;

import com.netflix.zuul.context.RequestContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.PRE_DECORATION_FILTER_ORDER;


public class LoginFilterTest {
    
    private LoginFilter loginFilter;

    @Before
    public void setUp() {
        UserChecker checker = new UserChecker();
        UserChecker.User user = new UserChecker.User();
        user.setPassword("admin");
        checker.getUser().put("admin", user);
        loginFilter = new LoginFilter(checker);
    }

    @After
    public void tearDown() {
        RequestContext.testSetCurrentContext(null);
    }

    @Test
    public void assertFilterType() {
        assertThat(loginFilter.filterType(), is("pre"));
    }

    @Test
    public void assertFilterOrder() {
        assertThat(loginFilter.filterOrder(), is(PRE_DECORATION_FILTER_ORDER + 1));
    }

    @Test
    public void assertShouldFilter() {
        RequestContext ctx = new RequestContext();
        ctx.set("requestURI", "/login/account");
        RequestContext.testSetCurrentContext(ctx);
        assertTrue(loginFilter.shouldFilter());
        ctx.set("requestURI", "/dashboard");
        assertFalse(loginFilter.shouldFilter());
    }
}