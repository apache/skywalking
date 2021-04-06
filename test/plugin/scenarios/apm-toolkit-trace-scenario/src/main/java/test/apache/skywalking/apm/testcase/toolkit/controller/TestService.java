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

package test.apache.skywalking.apm.testcase.toolkit.controller;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.apache.skywalking.apm.toolkit.model.User;
import org.apache.skywalking.apm.toolkit.trace.ActiveSpan;
import org.apache.skywalking.apm.toolkit.trace.CallableWrapper;
import org.apache.skywalking.apm.toolkit.trace.RunnableWrapper;
import org.apache.skywalking.apm.toolkit.trace.SupplierWrapper;
import org.apache.skywalking.apm.toolkit.trace.Tag;
import org.apache.skywalking.apm.toolkit.trace.Trace;
import org.springframework.stereotype.Component;

@Component
public class TestService {

    private static final ExecutorService SERVICE = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r);
        thread.setDaemon(true);
        return thread;
    });

    @Trace
    @Tag(key = "p1", value = "arg[0]")
    @Tag(key = "p2", value = "arg[1]")
    @Tag(key = "username", value = "returnedObj.username")
    public static User testStatic(final String username, final Integer age) {
        return new User(username, age);
    }

    @Trace
    public void testTag() {
        ActiveSpan.tag("key", "value");
    }

    @Trace
    @Tag(key = "p1", value = "arg[0]")
    @Tag(key = "p2", value = "arg[1]")
    public void testTagAnnotation(String param1, String param2) {
        // whatever
    }

    @Trace
    public void testError() {
        ActiveSpan.error();
    }

    @Trace
    public void testErrorMsg() {
        ActiveSpan.error("TestErrorMsg");
    }

    @Trace
    public void testErrorThrowable() {
        ActiveSpan.error(new RuntimeException("Test-Exception"));
    }

    @Trace
    public void testDebug() {
        ActiveSpan.debug("TestDebugMsg");
    }

    @Trace
    @Tag(key = "username", value = "returnedObj.username")
    public User testTagAnnotationReturnInfo(final String username, final Integer age) {
        return new User(username, age);
    }

    @Trace
    @Tag(key = "testTag", value = "arg[0]")
    public void testInfo(final String testInfoParam) {
        ActiveSpan.info("TestInfoMsg");
    }

    public void asyncRunnable(Runnable runnable) {
        SERVICE.submit(RunnableWrapper.of(runnable));
    }

    public void asyncCallable(Callable<Boolean> callable) {
        SERVICE.submit(CallableWrapper.of(callable));
    }

    public void asyncSupplier(Supplier<Boolean> supplier) {
        CompletableFuture.supplyAsync(SupplierWrapper.of(supplier));
    }

    @Trace
    public void testSetOperationName(String operationName) {
        ActiveSpan.setOperationName(operationName);
    }

}
