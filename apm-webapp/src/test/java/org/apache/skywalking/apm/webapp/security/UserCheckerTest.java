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

import org.junit.Test;

import static org.junit.Assert.*;

public class UserCheckerTest {

    @Test
    public void assertCheckSuccess() {
        UserChecker checker = new UserChecker();
        UserChecker.User user = new UserChecker.User();
        user.setPassword("888888");
        checker.getUser().put("admin", user);
        assertTrue(checker.check(new Account() {
            @Override public String userName() {
                return "admin";
            }

            @Override public String password() {
                return "888888";
            }
        }));
    }

    @Test
    public void assertCheckFail() {
        UserChecker checker = new UserChecker();
        UserChecker.User user = new UserChecker.User();
        user.setPassword("123456");
        checker.getUser().put("guest", user);
        assertFalse(checker.check(new Account() {
            @Override public String userName() {
                return "admin";
            }

            @Override public String password() {
                return "888888";
            }
        }));
        assertFalse(checker.check(new Account() {
            @Override public String userName() {
                return "guest";
            }

            @Override public String password() {
                return "888888";
            }
        }));
        assertFalse(checker.check(new Account() {
            @Override public String userName() {
                return "admin";
            }

            @Override public String password() {
                return "123456";
            }
        }));
        assertFalse(checker.check(new Account() {
            @Override public String userName() {
                return "";
            }

            @Override public String password() {
                return "123456";
            }
        }));
        assertFalse(checker.check(new Account() {
            @Override public String userName() {
                return "admin";
            }

            @Override public String password() {
                return "";
            }
        }));
    }
}