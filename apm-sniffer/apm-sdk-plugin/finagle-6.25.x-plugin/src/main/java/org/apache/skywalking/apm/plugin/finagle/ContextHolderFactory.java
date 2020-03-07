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
import scala.Option;
import scala.Predef;
import scala.Some;
import scala.Some$;
import scala.Tuple2;
import scala.collection.JavaConverters;
import scala.collection.JavaConverters$;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The implementation of {@link ContextHolder} depend on implementation of LocalContext and MarshalledContext of
 * finagle. To implement {@link ContextHolder}, we need know the actual data struct that the underlying Context use,
 * and use that data struct to impelment {@link ContextHolder#let(Object, Object)} and
 * {@link ContextHolder#remove(Object)}.
 */
public class ContextHolderFactory {

    private static final String CONTEXT_ENV_CLASS = "com.twitter.finagle.context.Context$Env";

    enum ContextImplType {
        /*
         * Compatible with versions 6.41.0 and below
         */
        ENV,
        /**
         * Compatible with versions above 6.41.0
         */
        MAP;
    }

    private static ContextImplType CONTEXTIMPL_TYPE;

    static {
        try {
            Class.forName(CONTEXT_ENV_CLASS);
            CONTEXTIMPL_TYPE = ContextImplType.ENV;
        } catch (ClassNotFoundException e) {
            CONTEXTIMPL_TYPE = ContextImplType.MAP;
        }
    }

    static ContextHolder getMarshalledContextHolder() {
        switch (CONTEXTIMPL_TYPE) {
            case ENV:
                return new EnvContextHolder(Contexts.broadcast());
            case MAP:
            default:
                return new MapMarshalledContextHolder(Contexts.broadcast());
        }
    }

    static ContextHolder getLocalContextHolder() {
        switch (CONTEXTIMPL_TYPE) {
            case ENV:
                return new EnvContextHolder(Contexts.local());
            case MAP:
            default:
                return new MapLocalContextHolder(Contexts.local());
        }
    }

    static abstract class AbstractContextHolder<T> extends ContextHolder {

        private static ConcurrentHashMap<Class<?>, Field> LOCAL_FIELDS = new ConcurrentHashMap<>();
        private static ConcurrentHashMap<Class<?>, Method> LOCAL_VALUE_METHODS = new ConcurrentHashMap<>();

        protected final Local<T> local;
        protected final T localValue;

        AbstractContextHolder(Context context, String localFieldName) {
            this.local = getLocal(context, getCachedLocalField(context, localFieldName));
            this.localValue = getLocalValue(context, getCachedLocalValueMethod(context));
        }

        @SuppressWarnings("unchecked")
        private Local<T> getLocal(Context context, Field localField) {
            try {
                return (Local<T>) localField.get(context);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @SuppressWarnings("unchecked")
        private T getLocalValue(Context context, Method localValueMethod) {
            try {
                return (T) localValueMethod.invoke(context);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private Field getCachedLocalField(final Context context, final String localFieldName) {
            return LOCAL_FIELDS.computeIfAbsent(context.getClass(), new Function<Class<?>, Field>() {
                @Override
                public Field apply(Class<?> aClass) {
                    return getLocalField(context, localFieldName);
                }
            });
        }

        private Method getCachedLocalValueMethod(final Context context) {
            return LOCAL_VALUE_METHODS.computeIfAbsent(context.getClass(), new Function<Class<?>, Method>() {
                @Override
                public Method apply(Class<?> aClass) {
                    return getLocalValueMethod(context);
                }
            });
        }
    }

    private static Field getLocalField(Context context, String localFieldName) {
        try {
            Field field = context.getClass().getDeclaredField(localFieldName);
            field.setAccessible(true);
            return field;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Method getLocalValueMethod(Context context) {
        try {
            Method method = context.getClass().getDeclaredMethod("env");
            method.setAccessible(true);
            return method;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static class EnvContextHolder extends AbstractContextHolder<Context.Env> {

        private static final String LOCAL_FIELD_NAME = "com$twitter$finagle$context$Context$$local";

        EnvContextHolder(Context context) {
            super(context, LOCAL_FIELD_NAME);
        }

        @Override
        void let(Object key, Object value) {
            if (local.apply().isDefined()) {
                local.update(local.apply().get().bound(key, value));
            } else {
                local.update(localValue.bound(key, value));
            }
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        <T> T remove(Object key) {
            if (local.apply().isDefined()) {
                Context.Env env = local.apply().get();
                Option<Object> option = env.get(key);
                if (option.isDefined()) {
                    local.update(env.cleared(key));
                    return (T) option.get();
                }
            }
            return null;
        }
    }

    static class MapLocalContextHolder extends ContextHolderFactory.AbstractContextHolder<scala.collection.immutable.Map<LocalContext.Key, Object>> {

        private static final String LOCAL_FIELD_NAME = "local";

        MapLocalContextHolder(LocalContext context) {
            super(context, LOCAL_FIELD_NAME);
        }

        @Override
        public void let(Object key, Object value) {
            checkKeyType(key);
            if (local.apply().isDefined()) {
                local.update(local.apply().get().updated((LocalContext.Key) key, value));
            } else {
                local.update(localValue.updated((LocalContext.Key) key, value));
            }
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T remove(Object key) {
            checkKeyType(key);
            if (local.apply().isDefined()) {
                scala.collection.immutable.Map<LocalContext.Key, Object> map = local.apply().get();
                if (map.contains((LocalContext.Key) key)) {
                    Map<LocalContext.Key, Object> javaMap =
                            new HashMap<>(JavaConverters$.MODULE$.mapAsJavaMapConverter(map).asJava());
                    Object value = javaMap.remove((LocalContext.Key) key);
                    local.update(toScalaMap(javaMap));
                    return (T) value;
                }
            }
            return null;
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
        private static final Field REAL_CONTENT_FIELD;

        static {
            try {
                Class<?> clz = Class.forName(MarshalledContext.class.getName() + "$Real");
                REAL_CONSTRUCTOR = clz.getDeclaredConstructor(MarshalledContext.class,
                        MarshalledContext.Key.class, Some.class);
                REAL_CONTENT_FIELD = clz.getDeclaredField("content");
                REAL_CONTENT_FIELD.setAccessible(true);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        MapMarshalledContextHolder(MarshalledContext context) {
            super(context, LOCAL_FIELD_NAME);
        }

        @Override
        public void let(Object key, Object value) {
            checkKeyType(key);
            try {
                MarshalledContext.Key marshalledContextKey = (MarshalledContext.Key) key;
                Object real = REAL_CONSTRUCTOR.newInstance(Contexts.broadcast(), marshalledContextKey, Some$.MODULE$.apply(value));
                if (local.apply().isDefined()) {
                    local.update(local.apply().get().updated(marshalledContextKey.marshalId(), real));
                } else {
                    local.update(localValue.updated(marshalledContextKey.marshalId(), real));
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T remove(Object key) {
            checkKeyType(key);
            try {
                MarshalledContext.Key marshalledContextKey = (MarshalledContext.Key) key;
                if (local.apply().isDefined()) {
                    scala.collection.immutable.Map<Buf, Object> map = local.apply().get();
                    if (map.contains(marshalledContextKey.marshalId())) {
                        Map<Buf, Object> javaMap =
                                new HashMap<>(JavaConverters$.MODULE$.mapAsJavaMapConverter(map).asJava());
                        Object value = javaMap.remove(marshalledContextKey.marshalId());
                        local.update(toScalaMap(javaMap));
                        return (T) value;
                    }
                }
                return null;
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

    private static <A, B> scala.collection.immutable.Map<A, B> toScalaMap(Map<A, B> m) {
        return JavaConverters.mapAsScalaMapConverter(m).asScala().toMap(Predef.<Tuple2<A, B>>conforms());
    }
}
