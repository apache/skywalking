///*
// * Licensed to the Apache Software Foundation (ASF) under one or more
// * contributor license agreements.  See the NOTICE file distributed with
// * this work for additional information regarding copyright ownership.
// * The ASF licenses this file to You under the Apache License, Version 2.0
// * (the "License"); you may not use this file except in compliance with
// * the License.  You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// *
// */
//
//package org.apache.skywalking.apm.webapp.security;
//
//import com.google.gson.Gson;
//import com.netflix.zuul.ZuulFilter;
//import com.netflix.zuul.context.RequestContext;
//import org.bouncycastle.asn1.ocsp.ResponseData;
//import org.springframework.stereotype.Component;
//import org.springframework.util.ReflectionUtils;
//
//import javax.servlet.http.HttpServletResponse;
//import java.io.IOException;
//
//import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.PRE_DECORATION_FILTER_ORDER;
//
///**
// * Filter login request.
// *
// * @author gaohongtao
// */
//@Component
//public class LogOutFilter extends ZuulFilter {
//
//    private static final String REQUEST_URI = "requestURI";
//
//    private static final String LOGIN_URI = "/logoutRequest";
//
//    private static final int ORDER = PRE_DECORATION_FILTER_ORDER + 1;
//
//    private final UserChecker checker;
//
//    public LogOutFilter(final UserChecker checker) {
//        this.checker = checker;
//    }
//
//    @Override
//    public String filterType() {
//        return "pre";
//    }
//
//    @Override
//    public int filterOrder() {
//        return ORDER;
//    }
//
//    @Override
//    public boolean shouldFilter() {
//        RequestContext ctx = RequestContext.getCurrentContext();
//        System.out.println("REQUEST_URI" + ctx.get(REQUEST_URI));
//        return ctx.get(REQUEST_URI).equals(LOGIN_URI);
//    }
//
//    @Override
//    public Object run() {
//        RequestContext ctx = RequestContext.getCurrentContext();
//        HttpServletResponse response = ctx.getResponse();
//
//        //session失效
//        ctx.getRequest().getSession().invalidate();
//        try {
//            response.sendRedirect("http://172.16.1.61:8080/logout?service=http://contract.test.renrendai.com:8080");
//            Gson gson = new Gson();
//            String resStr;
//            resStr = gson.toJson(new ResponseData("ok", "admin", "http://172.16.1.61:8080/logout?service=http://contract.test.renrendai.com:8080"));
//            response.setContentType("application/json");
//            response.setCharacterEncoding("UTF-8");
//            ctx.setResponseStatusCode(HttpServletResponse.SC_OK);
//            ctx.setResponseBody(resStr);
//            ctx.setSendZuulResponse(false);
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//
//        return null;
//    }
//
//    private static class ResponseData {
//        private final String status;
//        private final String currentAuthority;
//        private final String casServerLoginUrl;
//
//        ResponseData(final String status, final String currentAuthority, final String casServerLoginUrl) {
//            this.status = status;
//            this.currentAuthority = currentAuthority;
//            this.casServerLoginUrl = casServerLoginUrl;
//        }
//    }
//}
