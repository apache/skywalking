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

package org.apache.skywalking.apm.testcase.finagle;

import com.twitter.finagle.ListeningServer;
import com.twitter.finagle.Thrift;
import org.apache.skywalking.apm.testcase.finagle.interfaces.FinagleRpcDemoService;
import org.apache.skywalking.apm.testcase.finagle.service.FinagleRpcDemoServiceImpl;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@SpringBootApplication
public class FinagleRpcApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinagleRpcApplication.class, args);
    }

    @Configuration
    public static class FinagleRpcConfiguration {

        @Bean(destroyMethod = "close")
        public ListeningServer server() {
            ListeningServer server = Thrift.server()
                    .serveIface(":12220", new FinagleRpcDemoServiceImpl());
            return server;
        }

        @Bean
        public FinagleRpcDemoService.ServiceIface client() {
            return Thrift.client()
                    .newIface("localhost:12220", FinagleRpcDemoService.ServiceIface.class);
        }
    }
}
