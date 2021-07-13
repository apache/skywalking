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

package test.apache.skywalking.apm.testcase.spring3.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import test.apache.skywalking.apm.testcase.spring3.dao.TestRepositoryBean;
import test.apache.skywalking.apm.testcase.spring3.component.TestComponentBean;

@Service
public class TestServiceBean {
    public static final String URL = "http://localhost:8080/spring-3.0.x-scenario";

    @Autowired
    private TestComponentBean componentBean;

    @Autowired
    private TestRepositoryBean repositoryBean;

    public void doSomeBusiness(String name) {
        componentBean.componentMethod(name);
        repositoryBean.doSomeStuff(name);
    }

    public void doInvokeImplCase() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getForObject(URL + "/impl/requestmapping", String.class);
        restTemplate.getForObject(URL + "/impl/restmapping", String.class);
    }
}
