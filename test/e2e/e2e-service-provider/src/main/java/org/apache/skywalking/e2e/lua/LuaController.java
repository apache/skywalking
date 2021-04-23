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

package org.apache.skywalking.e2e.lua;

import lombok.RequiredArgsConstructor;
import org.apache.skywalking.apm.toolkit.trace.TraceContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
public class LuaController {
    protected final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/nginx/entry/info")
    private String nginxEntry(String backend) throws MalformedURLException, URISyntaxException {
        final URL url = new URL("http://nginx:8080/nginx/info");
        TraceContext.putCorrelation("entry", "entry_value");
        final ResponseEntity<String> response = restTemplate.postForEntity(url.toURI(), null, String.class);
        return response.getBody();
    }

    @PostMapping("/nginx/end/info")
    private String nginxEnd() throws InterruptedException {
        TimeUnit.SECONDS.sleep(1);

        return TraceContext.getCorrelation("entry").orElse("")
            + "_" + TraceContext.getCorrelation("nginx").orElse("");
    }

}
