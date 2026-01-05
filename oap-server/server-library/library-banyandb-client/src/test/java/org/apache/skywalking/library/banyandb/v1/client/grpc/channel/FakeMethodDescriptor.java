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

package org.apache.skywalking.library.banyandb.v1.client.grpc.channel;

import io.grpc.MethodDescriptor;

import java.io.InputStream;

public class FakeMethodDescriptor {
    private FakeMethodDescriptor() {
    }

    public static <I, O> MethodDescriptor<I, O> create() {
        return create(MethodDescriptor.MethodType.UNARY, "FakeClient/fake-method");
    }

    public static <I, O> MethodDescriptor<I, O> create(
            MethodDescriptor.MethodType type, String name) {
        return MethodDescriptor.<I, O>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(name)
                .setRequestMarshaller(new FakeMarshaller<I>())
                .setResponseMarshaller(new FakeMarshaller<O>())
                .build();
    }

    private static class FakeMarshaller<T> implements MethodDescriptor.Marshaller<T> {
        @Override
        public T parse(InputStream stream) {
            throw new UnsupportedOperationException("FakeMarshaller doesn't actually do anything");
        }

        @Override
        public InputStream stream(T value) {
            throw new UnsupportedOperationException("FakeMarshaller doesn't actually do anything");
        }
    }
}
