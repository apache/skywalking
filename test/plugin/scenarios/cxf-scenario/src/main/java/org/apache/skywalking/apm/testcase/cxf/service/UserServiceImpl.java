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

package org.apache.skywalking.apm.testcase.cxf.service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.jws.WebService;
import org.apache.skywalking.apm.testcase.cxf.entity.User;

@WebService
public class UserServiceImpl implements UserService {
    private Map<Long, User> userMap = new HashMap<Long, User>();

    public UserServiceImpl() {
        User user = new User();
        user.setUserId(1L);
        user.setUsername("skywalking");
        user.setGmtCreate(new Date());
        userMap.put(user.getUserId(), user);
    }

    @Override
    public String getName(Long userId) {
        return "hello" + userId;
    }

    @Override
    public User getUser(Long userId) {
        return userMap.get(userId);
    }

}
