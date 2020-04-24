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

import com.twitter.finagle.context.Context;
import com.twitter.finagle.context.Contexts;
import com.twitter.finagle.context.LocalContext;
import com.twitter.finagle.context.MarshalledContext;
import com.twitter.io.Buf;
import com.twitter.util.Local;
import scala.Some;
import scala.Some$;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedList;

/**
 * The implementation of {@link ContextHolder} depend on implementation detail of LocalContext and MarshalledContext of
 * finagle. To implement {@link ContextHolder}, we need know the actual data struct that the underlying Context use,
 * and use that data struct to impelment {@link ContextHolder#let(Object, Object)} and
 * {@link ContextHolder#remove(Object)}.
 */
class ContextHolderFactory {

    /*
     * Below version 6.41.0(inclusive), this class is used to implement Context, above version 6.41.0, ImmutableMap
     * is used. we check if this class is on the classpath, then we can know the actual data struct the underlying
     * finagle used.
     */
    private static final String CONTEXT_ENV_CLASS = "com.twitter.finagle.context.Context$Env";

    private static ContextHolder MARSHALLED_CONTEXT_HOLDER;
    private static ContextHolder LOCAL_CONTEXT_HOLDER;

    static {
        try {
            Class.forName(CONTEXT_ENV_CLASS);
            /*
             * Compatible with versions 6.41.0 and below
             */
            MARSHALLED_CONTEXT_HOLDER = new EnvContextHolder(Contexts.broadcast());
            LOCAL_CONTEXT_HOLDER = new EnvContextHolder(Contexts.local());
        } catch (ClassNotFoundException e) {
            /*
             * Compatible with versions above 6.41.0
             */
            LOCAL_CONTEXT_HOLDER = new MapLocalContextHolder(Contexts.local());
            MARSHALLED_CONTEXT_HOLDER = new MapMarshalledContextHolder(Contexts.broadcast());
        }
    }

    static ContextHolder getMarshalledContextHolder() {
        return MARSHALLED_CONTEXT_HOLDER;
    }

    static ContextHolder getLocalContextHolder() {
        return LOCAL_CONTEXT_HOLDER;
    }

    static abstract class AbstractContextHolder<S> extends ContextHolder {

        final Local<S> local;
        final S initContext;
        /**
         * We push each let operation to the stack, when there is a remove operation, the key must be the same with
         * key of let operation on the stack head.
         */
        private final ThreadLocal<LinkedList<Snapshot<S>>> snapshots;

        static class Snapshot<S> {
            /**
             * key from let operation
             */
            private Object key;
            /**
             * value of {@link #local} before current let operation
             */
            private S saved;

            private Snapshot(Object key, S saved) {
                this.key = key;
                this.saved = saved;
            }
        }

        AbstractContextHolder(Context context, String localFieldName) {
            this.local = getLocal(context, localFieldName);
            this.initContext = getInitContext(context);
            this.snapshots = new ThreadLocal<LinkedList<Snapshot<S>>>() {
                @Override
                protected LinkedList<Snapshot<S>> initialValue() {
                    return new LinkedList<>();
                }
            };
        }

        @Override
        void let(Object key, Object value) {
            S currentContext = getCurrentContext();
            snapshots.get().push(new Snapshot<>(key, currentContext));
            local.update(getUpdatedContext(currentContext, key, value));
        }

        @Override
        void remove(Object key) {
            Snapshot<S> snapshot = snapshots.get().peek();
            if (snapshot == null || !snapshot.key.equals(key)) {
                throw new IllegalStateException(String.format("can't remove %s. the order of remove must be opposite with" +
                        " let.", key));
            }
            local.update(snapshot.saved);
            snapshots.get().pop();
        }

        private S getCurrentContext() {
            if (local.apply().isDefined()) {
                return local.apply().get();
            }
            return initContext;
        }

        abstract protected S getUpdatedContext(S currentContext, Object key, Object value);

        @SuppressWarnings("unchecked")
        private Local<S> getLocal(Context context, String localFieldName) {
            try {
                Field localField = context.getClass().getDeclaredField(localFieldName);
                localField.setAccessible(true);
                return (Local<S>) localField.get(context);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @SuppressWarnings("unchecked")
        private S getInitContext(Context context) {
            try {
                Method method = context.getClass().getDeclaredMethod("env");
                method.setAccessible(true);
                return (S) method.invoke(context);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    static class EnvContextHolder extends AbstractContextHolder<Context.Env> {

        private static final String LOCAL_FIELD_NAME = "com$twitter$finagle$context$Context$$local";

        EnvContextHolder(Context context) {
            super(context, LOCAL_FIELD_NAME);
        }

        @Override
        protected Context.Env getUpdatedContext(Context.Env currentContext, Object key, Object value) {
            return currentContext.bound(key, value);
        }
    }

    static class MapLocalContextHolder extends ContextHolderFactory.AbstractContextHolder<scala.collection.immutable.Map<LocalContext.Key, Object>> {

        private static final String LOCAL_FIELD_NAME = "local";

        MapLocalContextHolder(LocalContext context) {
            super(context, LOCAL_FIELD_NAME);
        }

        @Override
        protected scala.collection.immutable.Map<LocalContext.Key, Object> getUpdatedContext(scala.collection.immutable.Map<LocalContext.Key, Object> currentContext, Object key, Object value) {
            checkKeyType(key);
            return currentContext.updated((LocalContext.Key) key, value);
        }

        private void checkKeyType(Object key) {
            if (!(key instanceof LocalContext.Key)) {
                throw new IllegalArgumentException("key should be subclass of LocalContext.Key");
            }
        }
    }

    static class MapMarshalledContextHolder extends ContextHolderFactory.AbstractContextHolder<scala.collection.immutable.Map<Buf, Object>> {

        private static final String LOCAL_FIELD_NAME = "local";

        private static final Constructor REAL_CONSTRUCTOR;

        static {
            try {
                Class<?> clz = Class.forName(MarshalledContext.class.getName() + "$Real");
                REAL_CONSTRUCTOR = clz.getDeclaredConstructor(MarshalledContext.class,
                        MarshalledContext.Key.class, Some.class);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        MapMarshalledContextHolder(MarshalledContext context) {
            super(context, LOCAL_FIELD_NAME);
        }

        @Override
        protected scala.collection.immutable.Map<Buf, Object> getUpdatedContext(scala.collection.immutable.Map<Buf, Object> currentContext, Object key, Object value) {
            checkKeyType(key);
            try {
                MarshalledContext.Key marshalledContextKey = (MarshalledContext.Key) key;
                Object real = REAL_CONSTRUCTOR.newInstance(Contexts.broadcast(), marshalledContextKey, Some$.MODULE$.apply(value));
                return currentContext.updated(marshalledContextKey.marshalId(), real);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private void checkKeyType(Object key) {
            if (!(key instanceof MarshalledContext.Key)) {
                throw new IllegalArgumentException("key should be subclass of MarshalledContext.Key");
            }
        }
    }
}
