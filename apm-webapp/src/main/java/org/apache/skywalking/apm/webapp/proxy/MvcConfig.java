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


import org.apache.skywalking.apm.webapp.security.PermissionFilter;
import org.jasig.cas.client.authentication.AuthenticationFilter;
import org.jasig.cas.client.session.SingleSignOutFilter;
import org.jasig.cas.client.session.SingleSignOutHttpSessionListener;
import org.jasig.cas.client.util.AssertionThreadLocalFilter;
import org.jasig.cas.client.util.HttpServletRequestWrapperFilter;
import org.jasig.cas.client.validation.Cas20ProxyReceivingTicketValidationFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.util.Arrays;

/**
 * Additional MVC Configuration.
 *
 * @author gaohongtao
 */
@Configuration
public class MvcConfig extends WebMvcConfigurerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(MvcConfig.class);

    @Override
    public void addResourceHandlers(final ResourceHandlerRegistry registry) {
        registry
                .addResourceHandler("/img/node/**")
                .addResourceLocations("classpath:/public/img/node/")
                .setCachePeriod(3600)
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource raw = super.getResource(resourcePath, location);
                        if (raw != null) {
                            return raw;
                        }
                        Resource resource = location.createRelative("UNDEFINED.png");
                        if (!resource.exists() || !resource.isReadable()) {
                            return null;
                        }
                        if (this.checkResource(resource, location)) {
                            return resource;
                        }

                        if (this.logger.isTraceEnabled()) {
                            Resource[] allowedLocations = this.getAllowedLocations();
                            this.logger.trace("Resource path \"" + resourcePath + "\" was successfully resolved but resource \"" + resource.getURL() + "\" is neither under the current location \"" + location.getURL() + "\" nor under any of the allowed locations " + (allowedLocations != null ? Arrays.asList(allowedLocations) : "[]"));
                        }
                        return null;
                    }
                });
    }
    @Bean
    public ServletListenerRegistrationBean<SingleSignOutHttpSessionListener> singleSignOutHttpSessionListener() {
        ServletListenerRegistrationBean<SingleSignOutHttpSessionListener> listener = new ServletListenerRegistrationBean<>();
        listener.setEnabled(true);
        listener.setListener(new SingleSignOutHttpSessionListener());
        listener.setOrder(1);
        return listener;
    }

    @Bean
    public FilterRegistrationBean signOutFilter() {
        FilterRegistrationBean registration = new FilterRegistrationBean();
        try {

            SingleSignOutFilter singleSignOutFilter = new SingleSignOutFilter();
            singleSignOutFilter.setIgnoreInitConfiguration(true);
            singleSignOutFilter.setCasServerUrlPrefix("http://172.16.1.61:8080");
            singleSignOutFilter.setFrontLogoutParameterName("logoutRequest");
            singleSignOutFilter.setRelayStateParameterName("RelayState");
//            registration.addInitParameter("serverName","contract.test.renrendai.com");
            registration.setFilter(singleSignOutFilter);
            registration.addUrlPatterns("/*");
            registration.setName("CAS Single Sign Out Filter");
            registration.setOrder(2);

        }catch (Exception e) {
            e.printStackTrace();
        }
        return registration;
    }



    /**
     * 登录filter
     * @return
     */
    @Bean
    public FilterRegistrationBean authenticationFilter() {
        FilterRegistrationBean registration = new FilterRegistrationBean();
        AuthenticationFilter authenticationFilter = new AuthenticationFilter();
        registration.setFilter(authenticationFilter);
        registration.addInitParameter("casServerLoginUrl","http://172.16.1.61:8080");
        registration.addInitParameter("serverName","contract.test.renrendai.com");
        registration.addUrlPatterns("/*");
        registration.setName("CASFilter");
        registration.setOrder(3);
        return registration;
    }
    @Bean
    public FilterRegistrationBean validationFilter() {
        FilterRegistrationBean registration = new FilterRegistrationBean();
        Cas20ProxyReceivingTicketValidationFilter cas20ProxyReceivingTicketValidationFilter = new Cas20ProxyReceivingTicketValidationFilter();
        registration.setFilter(cas20ProxyReceivingTicketValidationFilter);
        registration.addInitParameter("casServerUrlPrefix","http://172.16.1.61:8080");
        registration.addInitParameter("serverName","contract.test.renrendai.com");
        registration.addUrlPatterns("/*");
        registration.setName("CAS Validation Filter");
        registration.setOrder(4);
        return registration;
    }
    @Bean
    public FilterRegistrationBean wrapperFilter() {
        FilterRegistrationBean registration = new FilterRegistrationBean();
        HttpServletRequestWrapperFilter httpServletRequestWrapperFilter = new HttpServletRequestWrapperFilter();
        registration.setFilter(httpServletRequestWrapperFilter);
        registration.addUrlPatterns("/*");
        registration.setName("CAS HttpServletRequest Wrapper Filter");
        registration.setOrder(5);
        return registration;
    }
    @Bean
    public FilterRegistrationBean threadLocalFilter() {
        FilterRegistrationBean registration = new FilterRegistrationBean();
        AssertionThreadLocalFilter assertionThreadLocalFilter = new AssertionThreadLocalFilter();
        registration.setFilter(assertionThreadLocalFilter);
        registration.addUrlPatterns("/*");
        registration.setName("CAS Assertion Thread Local Filter");
        registration.setOrder(6);
        return registration;
    }
    @Bean
    public FilterRegistrationBean permissionFilter() {
        FilterRegistrationBean registration = new FilterRegistrationBean();
        PermissionFilter permissionFilter = new PermissionFilter();
        registration.setFilter(permissionFilter);
        registration.addUrlPatterns("/*");
        registration.setName("permissionFilter");
        registration.setOrder(7);
        return registration;
    }



}