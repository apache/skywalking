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

package org.apache.skywalking.oap.server.cluster.plugin.kubernetes.fixture;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CountDownLatch;
import org.apache.skywalking.oap.server.cluster.plugin.kubernetes.Event;
import org.apache.skywalking.oap.server.cluster.plugin.kubernetes.ReusableWatch;

public class PlainWatch implements ReusableWatch<Event> {

    public static PlainWatch create(final int size, final String... args) {
        List<Event> events = new ArrayList<>(args.length / 3);
        for (int i = 0; i < args.length; i++) {
            events.add(new Event(args[i++], args[i++], args[i]));
        }
        return new PlainWatch(events, size);
    }

    private final List<Event> events;

    private final int size;

    private final CountDownLatch latch = new CountDownLatch(1);

    private Iterator<Event> iterator;

    private int count;

    private PlainWatch(final List<Event> events, final int size) {
        this.events = events;
        this.size = size;
    }

    @Override public void initOrReset() {
        final Iterator<Event> internal = events.subList(count, events.size()).iterator();
        iterator = new Iterator<Event>() {
            public boolean hasNext() {
                boolean result =  count < size && internal.hasNext();
                if (!result) {
                    latch.countDown();
                }
                return result;
            }

            public Event next() {
                if (!this.hasNext()) {
                    throw new NoSuchElementException();
                } else {
                    ++count;
                    return internal.next();
                }
            }

            public void remove() {
                internal.remove();
            }
        };
    }

    @Override public Iterator<Event> iterator() {
        return iterator;
    }

    public void await() throws InterruptedException {
        latch.await();
    }
}
