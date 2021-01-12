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

package org.apache.skywalking.apm.testcase.dubbo;

import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.skywalking.apm.testcase.dubbo.services.GreetService;
import org.apache.skywalking.apm.testcase.dubbo.services.impl.GreetServiceImpl;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Configuration
    public static class DubboConfiguration {

        private ApplicationConfig applicationConfig = new ApplicationConfig(Application.class.getSimpleName());

        private RegistryConfig registryConfig = new RegistryConfig("N/A");

        private ProtocolConfig protocolConfig = new ProtocolConfig("dubbo", 20080);

        @Bean(destroyMethod = "unexport")
        public ServiceConfig<GreetService> service() {
            ServiceConfig<GreetService> serviceConfig = new ServiceConfig<>();
            serviceConfig.setApplication(applicationConfig);
            serviceConfig.setRegistry(registryConfig);
            serviceConfig.setProtocol(protocolConfig);
            serviceConfig.setInterface(GreetService.class);
            serviceConfig.setRef(new GreetServiceImpl());
            serviceConfig.setTimeout(5000);
            serviceConfig.export();
            return serviceConfig;
        }

        @Bean(destroyMethod = "destroy")
        public ReferenceConfig<GreetService> reference() {
            ReferenceConfig<GreetService> referenceConfig = new ReferenceConfig<>();
            referenceConfig.setApplication(applicationConfig);

            referenceConfig.setInterface(GreetService.class);
            referenceConfig.setUrl("dubbo://localhost:20080");

            return referenceConfig;
        }
    }
}
