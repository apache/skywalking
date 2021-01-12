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
 */

package org.apache.skywalking.apm.testcase.armeria;

import com.linecorp.armeria.client.WebClient;
import java.nio.charset.StandardCharsets;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ClientApplication {

    private WebClient httpClient = WebClient.of("http://127.0.0.1:8085/");

    @GetMapping("/healthCheck")
    public String healthCheck() {
        return httpClient.get("/healthCheck").aggregate().join().content().toString(StandardCharsets.UTF_8);
    }

    @GetMapping("/greet/{username}")
    public String greet(@PathVariable String username) {
        return httpClient.get("/greet/" + username).aggregate().join().content().toString(StandardCharsets.UTF_8);
    }
}
