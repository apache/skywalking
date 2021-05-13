/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.skywalking.apm.testcase.spring.async;

import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class AsyncBean {

    @Autowired
    private HttpBean httpBean;

    @Async
    public void sendVisitBySystem() throws IOException {
        httpBean.visit("http://localhost:8080/spring-async-scenario/case/asyncVisit?by=system");
    }

    @Async("customizeAsync")
    public void sendVisitByCustomize() throws IOException {
        httpBean.visit("http://localhost:8080/spring-async-scenario/case/asyncVisit?by=customize");
    }
}
