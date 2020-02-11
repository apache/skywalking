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

package org.apache.skywalking.oap.server.receiver.zipkin.analysis.data;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import zipkin2.Span;

public class ZipkinTrace {
    private List<Span> spans;
    private ReentrantLock spanWriteLock;

    public ZipkinTrace() {
        spans = new LinkedList<>();
        spanWriteLock = new ReentrantLock();
    }

    public void addSpan(Span span) {
        spanWriteLock.lock();
        try {
            spans.add(span);
        } finally {
            spanWriteLock.unlock();
        }
    }

    public List<Span> getSpans() {
        return spans;
    }

    @Override
    public String toString() {
        return "ZipkinTrace{" + "spans=" + spans + '}';
    }

    public static class TriggerTrace extends ZipkinTrace {

    }
}
