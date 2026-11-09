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

package org.apache.skywalking.oap.trace.analysis.checker;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * The wire index of the span a listener callback or a rule run is working on, set by the wrappers each side puts
 * around them, so an event made during a span's run carries the span it was made for.
 */
final class CheckerWireIndex {
    private static final ThreadLocal<Integer> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<IntConsumer> RUN_LISTENER = new ThreadLocal<>();

    private CheckerWireIndex() {
    }

    /**
     * @return the wire index of the span being worked on, or null outside a span's run
     */
    static Integer current() {
        return CURRENT.get();
    }

    static void clear() {
        CURRENT.remove();
        RUN_LISTENER.remove();
    }

    /**
     * @param listener told the wire index of each span run that starts, until {@link #clear()}
     */
    static void onRun(final IntConsumer listener) {
        RUN_LISTENER.set(listener);
    }

    /**
     * Call the method with the index set. On a throw the index stays set, so the exception the side records is
     * annotated with the span it was thrown for.
     */
    static Object invokeAt(final int index, final Method method, final Object target, final Object[] args)
        throws Throwable {
        final Integer previous = CURRENT.get();
        final IntConsumer listener = RUN_LISTENER.get();
        if (listener != null) {
            listener.accept(index);
        }
        CURRENT.set(index);
        final Object result;
        try {
            result = method.invoke(target, args);
        } catch (final InvocationTargetException e) {
            throw e.getCause();
        }
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
        return result;
    }

    /**
     * @return the position of the element in the list, by identity, or -1; a parsed segment holds a distinct object per
     * span, so identity tells two equal spans apart
     */
    static int indexOf(final List<?> list, final Object element) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == element) {
                return i;
            }
        }
        return -1;
    }

    static Object invoke(final Method method, final Object target, final Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (final InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
