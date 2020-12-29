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

package test.apache.skywalking.apm.testcase.resttemplate;

import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestTemplate;
import test.apache.skywalking.apm.testcase.entity.User;

@Controller
public class RestTemplateController {

    private static final String SUCCESS = "Success";

    private static final Logger LOGGER = LogManager.getLogger(RestTemplateController.class);

    private static final String URL = "http://localhost:8080/spring-4.1.x-scenario";

    @RequestMapping("/case/resttemplate")
    @ResponseBody
    public String restTemplate() throws IOException {
        Request request = new Request.Builder().url(URL + "/case/spring3/").build();
        Response response = new OkHttpClient().newCall(request).execute();
        LOGGER.info(response.toString());

        // Create user
        HttpEntity<User> userEntity = new HttpEntity<>(new User(1, "a"));
        new RestTemplate().postForEntity(URL + "/create/", userEntity, Void.class);

        // Find User
        new RestTemplate().getForEntity(URL + "/get/{id}", User.class, 1);

        //Modify user
        HttpEntity<User> updateUserEntity = new HttpEntity<>(new User(1, "b"));
        new RestTemplate().put(URL + "/update/{id}", updateUserEntity, userEntity.getBody().getId(), 1);

        //Delete user
        new RestTemplate().delete(URL + "/delete/{id}", 1);

        return SUCCESS;
    }

    @RequestMapping("/healthCheck")
    @ResponseBody
    public String healthCheck() {
        return SUCCESS;
    }

}
