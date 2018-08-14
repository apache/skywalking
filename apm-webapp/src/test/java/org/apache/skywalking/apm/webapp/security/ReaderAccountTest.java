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

import java.io.BufferedReader;
import java.io.StringReader;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

public class ReaderAccountTest {

    @Test
    public void assertNewReaderAccount() {
        Account account = ReaderAccount.newReaderAccount(new BufferedReader(new StringReader("{\"userName\": \"admin\", \"password\":\"888888\"}")));
        assertThat(account.userName(), is("admin"));
        assertThat(account.password(), is("888888"));
    }
    
}