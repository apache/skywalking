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

package org.apache.skywalking.apm.testcase.servicecomb.provider;

import javax.ws.rs.core.MediaType;
import org.apache.servicecomb.provider.pojo.RpcSchema;
import org.apache.skywalking.apm.testcase.servicecomb.schema.Hello;
import org.apache.skywalking.apm.testcase.servicecomb.schema.models.Person;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@RpcSchema(schemaId = "codeFirstSpringmvcHello")
@RequestMapping(path = "/springmvchello", produces = MediaType.APPLICATION_JSON)
public class CodeFirstSpringmvcHelloImpl implements Hello {

    @Override
    @RequestMapping(path = "/sayhi", method = RequestMethod.POST)
    public String sayHi(@RequestParam(name = "name") String name) {
        return "Spring mvc Hello " + name;
    }

    @Override
    @RequestMapping(path = "/sayhello", method = RequestMethod.POST)
    public String sayHello(@RequestBody Person person) {
        return "Spring mvc Hello person " + person.getName();
    }
}
