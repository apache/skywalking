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

package org.apache.skywalking.apm.testcase.sofarpc;

import com.alipay.sofa.rpc.config.ConsumerConfig;
import com.alipay.sofa.rpc.config.ProviderConfig;
import com.alipay.sofa.rpc.config.ServerConfig;
import org.apache.skywalking.apm.testcase.sofarpc.interfaces.SofaRpcDemoService;
import org.apache.skywalking.apm.testcase.sofarpc.service.SofaRpcDemoServiceImpl;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@SpringBootApplication
public class SofaRpcApplication {

    public static void main(String[] args) {
        SpringApplication.run(SofaRpcApplication.class, args);
    }

    @Configuration
    public static class SofaRpcConfiguration {

        @Bean(destroyMethod = "unExport")
        public ProviderConfig provider() {
            ServerConfig config = new ServerConfig().setProtocol("bolt").setPort(12200).setDaemon(true);

            ProviderConfig<SofaRpcDemoService> providerConfig = new ProviderConfig<SofaRpcDemoService>().setInterfaceId(SofaRpcDemoService.class
                .getName()).setRef(new SofaRpcDemoServiceImpl()).setServer(config);

            providerConfig.export();
            return providerConfig;
        }

        @Bean
        public ConsumerConfig consumer() {
            return new ConsumerConfig<SofaRpcDemoService>().setInterfaceId(SofaRpcDemoService.class.getName())
                                                           .setProtocol("bolt")
                                                           .setDirectUrl("bolt://127.0.0.1:12200");
        }
    }
}
