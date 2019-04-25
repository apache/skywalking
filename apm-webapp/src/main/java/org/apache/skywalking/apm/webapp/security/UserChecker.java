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

package org.apache.skywalking.apm.webapp.security;

import com.google.common.base.Strings;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * A Checker to check username and password.
 * 
 * @author gaohongtao
 */
@Component
@ConfigurationProperties(prefix = "security")
public class UserChecker {
    
    private Map<String, User> user = new HashMap<>();

    public Map<String, User> getUser() {
        return user;
    }

    boolean check(Account account) {
        if (Strings.isNullOrEmpty(account.userName()) || Strings.isNullOrEmpty(account.password())) {
            return false;
        }
        if (!user.containsKey(account.userName())) {
            return false;
        }
        return user.get(account.userName()).password.equals(account.password());
    }
    
    public static class User {
        private String password;

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
