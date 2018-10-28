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

import java.io.IOException;
import java.util.Arrays;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Additional MVC Configuration.
 * 
 * @author gaohongtao
 */
@Configuration
public class MvcConfig extends WebMvcConfigurerAdapter {
    
    @Override
    public void addResourceHandlers(final ResourceHandlerRegistry registry) {
        registry
            .addResourceHandler("/img/node/**")
            .addResourceLocations("classpath:/public/img/node/")
            .setCachePeriod(3600)
            .resourceChain(true)
            .addResolver(new PathResourceResolver() {
                @Override protected Resource getResource(String resourcePath, Resource location) throws IOException {
                    Resource raw =  super.getResource(resourcePath, location);
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
}