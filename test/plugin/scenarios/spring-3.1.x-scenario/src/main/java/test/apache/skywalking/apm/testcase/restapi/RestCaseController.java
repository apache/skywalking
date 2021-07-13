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

package test.apache.skywalking.apm.testcase.restapi;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.util.UriComponentsBuilder;
import test.apache.skywalking.apm.testcase.entity.User;

@Controller
public class RestCaseController {

    private static final Map<Integer, User> USERS = new ConcurrentHashMap<Integer, User>();

    @RequestMapping(value = "/get/{id}", method = RequestMethod.GET)
    @ResponseBody
    private User getUser(@PathVariable("id") int id) throws InterruptedException {
        return new User(id, "a");
    }

    @RequestMapping(value = "/create/", method = RequestMethod.POST)
    @ResponseBody
    @ResponseStatus(HttpStatus.CREATED)
    public void createUser(@RequestBody User user, HttpServletResponse response,
        UriComponentsBuilder ucBuilder) throws InterruptedException {
        USERS.put(user.getId(), user);
        response.setHeader("Location", ucBuilder.path("/get/{id}")
                                                .buildAndExpand(user.getId())
                                                .toUri()
                                                .toASCIIString());
    }

    @RequestMapping(value = "/update/{id}", method = RequestMethod.PUT)
    @ResponseBody
    public User updateUser(@PathVariable("id") int id, @RequestBody User user) throws InterruptedException {
        return new User(id, user.getUserName());
    }

    @RequestMapping(value = "/delete/{id}", method = RequestMethod.DELETE)
    @ResponseBody
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable("id") int id) throws InterruptedException {
        User currentUser = USERS.get(id);
        if (currentUser == null) {
            return;
        }
        USERS.remove(id);
    }
}
