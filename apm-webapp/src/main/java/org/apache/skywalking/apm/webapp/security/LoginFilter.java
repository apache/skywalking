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

import com.google.gson.Gson;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.PRE_DECORATION_FILTER_ORDER;

/**
 * Filter login request.
 * 
 * @author gaohongtao
 */
@Component
public class LoginFilter extends ZuulFilter {

    private static final String REQUEST_URI = "requestURI";
    
    private static final String LOGIN_URI = "/login/account";

    private static final int ORDER = PRE_DECORATION_FILTER_ORDER + 1;
    
    private final UserChecker checker;
    
    public LoginFilter(final UserChecker checker) {
        this.checker = checker;
    }

    @Override public String filterType() {
        return "pre";
    }

    @Override public int filterOrder() {
        return ORDER;
    }

    @Override public boolean shouldFilter() {
        RequestContext ctx = RequestContext.getCurrentContext();
        return ctx.get(REQUEST_URI).equals(LOGIN_URI);
    }

    @Override public Object run() {
        RequestContext ctx = RequestContext.getCurrentContext();
        Account loginAccount = null;
        try {
            loginAccount = ReaderAccount.newReaderAccount(ctx.getRequest().getReader());
        } catch (IOException e) {
            ReflectionUtils.rethrowRuntimeException(e);
        }
        Gson gson = new Gson();
        String resStr;
        if (checker.check(loginAccount)) {
            resStr = gson.toJson(new ResponseData("ok", "admin"));
        } else {
            resStr = gson.toJson(new ResponseData("error", "guest"));
        }
        HttpServletResponse response = ctx.getResponse();
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        ctx.setResponseStatusCode(HttpServletResponse.SC_OK);
        ctx.setResponseBody(resStr);
        ctx.setSendZuulResponse(false);
        return null;
    }
    
    private static class ResponseData {
        private final String status;
        private final String currentAuthority;
        ResponseData(final String status, final String currentAuthority) {
            this.status = status;
            this.currentAuthority = currentAuthority;
        }
    }
}
