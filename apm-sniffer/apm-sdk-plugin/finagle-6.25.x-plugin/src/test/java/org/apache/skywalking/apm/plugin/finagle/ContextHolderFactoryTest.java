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

package org.apache.skywalking.apm.plugin.finagle;

import com.twitter.finagle.context.Contexts;
import com.twitter.finagle.context.LocalContext;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.lang.reflect.Constructor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.apache.skywalking.apm.plugin.finagle.ContextHolderFactory.getLocalContextHolder;
import static org.apache.skywalking.apm.plugin.finagle.ContextHolderFactory.getMarshalledContextHolder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ContextHolderFactoryTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private static LocalContext.Key<String> KEY1;
    private static LocalContext.Key<String> KEY2;
    private static LocalContext.Key<String> KEY3;

    @BeforeClass
    public static void setup() throws Exception {
        Constructor constructor = LocalContext.Key.class.getConstructor(LocalContext.class);
        KEY1 = (LocalContext.Key<String>) constructor.newInstance(Contexts.local());
        KEY2 = (LocalContext.Key<String>) constructor.newInstance(Contexts.local());
        KEY3 = (LocalContext.Key<String>) constructor.newInstance(Contexts.local());
    }

    @Test
    public void testLCRemoveNonExistKey() {
        testRemoveNonExistKey(getLocalContextHolder());
    }

    @Test
    public void testLCRemoveWrongOrder() {
        ContextHolder localContextHolder = getLocalContextHolder();
        try {
            localContextHolder.let(KEY1, "key1");
            localContextHolder.let(KEY2, "key2");
            testLCRemoveWrongOrder(localContextHolder, KEY1);
        } finally {
            localContextHolder.remove(KEY2);
            localContextHolder.remove(KEY1);
        }
    }

    @Test
    public void testLCLetAndRemove() {
        ContextHolder localContextHolder = getLocalContextHolder();
        localContextHolder.let(KEY1, Thread.currentThread().getName() + KEY1);
        localContextHolder.let(KEY2, Thread.currentThread().getName() + KEY2);
        localContextHolder.let(KEY3, Thread.currentThread().getName() + KEY3);
        localContextHolder.let(KEY2, Thread.currentThread().getName() + KEY2 + KEY2);

        assertEquals(Contexts.local().apply(KEY1), Thread.currentThread().getName() + KEY1);
        assertEquals(Contexts.local().apply(KEY2), Thread.currentThread().getName() + KEY2 + KEY2);
        assertEquals(Contexts.local().apply(KEY3), Thread.currentThread().getName() + KEY3);

        localContextHolder.remove(KEY2);
        assertEquals(Contexts.local().apply(KEY1), Thread.currentThread().getName() + KEY1);
        assertEquals(Contexts.local().apply(KEY2), Thread.currentThread().getName() + KEY2);
        assertEquals(Contexts.local().apply(KEY3), Thread.currentThread().getName() + KEY3);

        localContextHolder.remove(KEY3);
        assertEquals(Contexts.local().apply(KEY1), Thread.currentThread().getName() + KEY1);
        assertEquals(Contexts.local().apply(KEY2), Thread.currentThread().getName() + KEY2);
        assertFalse(Contexts.local().get(KEY3).isDefined());

        localContextHolder.remove(KEY2);
        assertEquals(Contexts.local().apply(KEY1), Thread.currentThread().getName() + KEY1);
        assertFalse(Contexts.local().get(KEY2).isDefined());
        assertFalse(Contexts.local().get(KEY3).isDefined());

        localContextHolder.remove(KEY1);
        assertFalse(Contexts.local().get(KEY1).isDefined());
        assertFalse(Contexts.local().get(KEY2).isDefined());
        assertFalse(Contexts.local().get(KEY3).isDefined());
    }

    @Test
    public void testLCLetAndRemoveMultiThread() throws InterruptedException {
        ExecutorService exec = Executors.newFixedThreadPool(5);
        final AtomicInteger exceptions = new AtomicInteger(0);
        for (int i = 0; i < 30; i++) {
            exec.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        testLCLetAndRemove();
                    } catch (Exception e) {
                        exceptions.incrementAndGet();
                    }
                }
            });
        }
        exec.shutdown();
        exec.awaitTermination(3, TimeUnit.MINUTES);
        assertEquals(exceptions.get(), 0);
    }

    @Test
    public void testMCRemoveNonExistKey() {
        testRemoveNonExistKey(getMarshalledContextHolder());
    }

    private void testRemoveNonExistKey(ContextHolder contextHolder) {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("can't remove");
        contextHolder.remove(new Object());
    }

    private void testLCRemoveWrongOrder(ContextHolder contextHolder, Object key) {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("can't remove");
        thrown.expectMessage(key.toString());
        contextHolder.remove(key);
    }
}
