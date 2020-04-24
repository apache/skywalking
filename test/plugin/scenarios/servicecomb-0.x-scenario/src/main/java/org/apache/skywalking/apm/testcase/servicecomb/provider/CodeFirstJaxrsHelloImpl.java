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

import io.servicecomb.provider.pojo.RpcSchema;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import org.apache.skywalking.apm.testcase.servicecomb.schema.Hello;
import org.apache.skywalking.apm.testcase.servicecomb.schema.models.Person;

@RpcSchema(schemaId = "codeFirstJaxrsHello")
@Path("/codefirstjaxrshello")
@Produces(MediaType.APPLICATION_JSON)
public class CodeFirstJaxrsHelloImpl implements Hello {

    @Path("/sayhi")
    @POST
    @Override
    public String sayHi(String name) {
        return "Jaxrs Hello " + name;
    }

    @Path("/sayhello")
    @POST
    @Override
    public String sayHello(Person person) {
        return "Jaxrs Hello person " + person.getName();
    }
}
