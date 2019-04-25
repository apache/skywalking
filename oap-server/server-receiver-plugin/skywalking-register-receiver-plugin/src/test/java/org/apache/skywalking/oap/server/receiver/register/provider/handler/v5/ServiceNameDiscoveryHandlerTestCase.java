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

package org.apache.skywalking.oap.server.receiver.register.provider.handler.v5;

import io.grpc.*;
import org.apache.skywalking.apm.network.language.agent.*;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class ServiceNameDiscoveryHandlerTestCase {

    private static final Logger logger = LoggerFactory.getLogger(ServiceNameDiscoveryHandlerTestCase.class);

    public static void main(String[] args) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 11800).usePlaintext(true).build();
        ServiceNameDiscoveryServiceGrpc.ServiceNameDiscoveryServiceBlockingStub stub = ServiceNameDiscoveryServiceGrpc.newBlockingStub(channel);

        ServiceNameCollection.Builder serviceNameCollection = ServiceNameCollection.newBuilder();
        ServiceNameElement.Builder serviceNameElement = ServiceNameElement.newBuilder();
        serviceNameElement.setApplicationId(1);
        serviceNameElement.setServiceName("org.skywaking.apm.testcase.dubbo.services.GreetService.doBusiness()");
        serviceNameElement.setSrcSpanType(SpanType.Entry);
        serviceNameCollection.addElements(serviceNameElement);

        ServiceNameMappingCollection collection = stub.discovery(serviceNameCollection.build());

        for (ServiceNameMappingElement element : collection.getElementsList()) {
            logger.info("service id: {}", element.getServiceId());
        }
    }
}
