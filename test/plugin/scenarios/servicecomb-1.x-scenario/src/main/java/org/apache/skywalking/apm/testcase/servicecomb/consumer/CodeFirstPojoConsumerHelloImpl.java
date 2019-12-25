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

package org.apache.skywalking.apm.testcase.servicecomb.consumer;

import org.apache.log4j.Logger;
import org.apache.servicecomb.provider.pojo.RpcReference;
import org.apache.servicecomb.provider.pojo.RpcSchema;
import org.apache.skywalking.apm.testcase.servicecomb.schema.Hello;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@RpcSchema(schemaId = "codeFirstSpringmvcHelloClient")
@RequestMapping(path = "/servicecomb")
public class CodeFirstPojoConsumerHelloImpl {
    private static Logger LOGGER = Logger.getLogger(CodeFirstPojoConsumerHelloImpl.class);
    @RpcReference(microserviceName = "codefirst", schemaId = "codeFirstHello")
    private Hello hello;

    @RequestMapping(path = "/case", method = RequestMethod.GET)
    public String say() {
        String repo = " sayHi invoke filed";
        try {
            repo = hello.sayHi("Java Chassis");
        } catch (Exception e) {
            LOGGER.error("sayHi invoke filed");
        }
        return repo;
    }

    @RequestMapping(path = "/healthCheck", method = {RequestMethod.HEAD})
    public String healthCheck() {
        return "Success";
    }
}
