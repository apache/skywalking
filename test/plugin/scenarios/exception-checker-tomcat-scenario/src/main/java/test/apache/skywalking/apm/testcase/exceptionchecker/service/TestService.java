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

package test.apache.skywalking.apm.testcase.exceptionchecker.service;

import org.springframework.stereotype.Service;
import test.apache.skywalking.apm.testcase.exceptionchecker.exception.TestAnnotatedException;
import test.apache.skywalking.apm.testcase.exceptionchecker.exception.TestException;
import test.apache.skywalking.apm.testcase.exceptionchecker.exception.TestHierarchyListedException;
import test.apache.skywalking.apm.testcase.exceptionchecker.exception.TestListedException;

@Service
public class TestService {

    public String testAnnotatedException() {
        throw new TestAnnotatedException();
    }

    public String testException() {
        throw new TestException();
    }

    public String testListedException() {
        throw new TestListedException();
    }

    public String testHierarchyListedException() {
        throw new TestHierarchyListedException();
    }

    public String testRecursiveException() {
        throw new TestException(new TestListedException());
    }

}
