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

package org.apache.skywalking.library.banyandb.v1.client.auth;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import org.apache.skywalking.library.banyandb.v1.client.grpc.channel.FakeMethodDescriptor;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

public class AuthInterceptorTest {
    private static final Metadata.Key<String> USERNAME_KEY =
            Metadata.Key.of("username", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> PASSWORD_KEY =
            Metadata.Key.of("password", Metadata.ASCII_STRING_MARSHALLER);

    @Test
    public void shouldSendConfiguredCredentials() {
        Metadata headers = startCall(new AuthInterceptor("admin", "123456"));

        assertEquals("admin", headers.get(USERNAME_KEY));
        assertEquals("123456", headers.get(PASSWORD_KEY));
    }

    /**
     * The point of the whole mechanism: the same interceptor instance, over the same channel, carries the
     * rotated credentials on the next call. No reconnect, no channel rebuild.
     */
    @Test
    public void shouldSendRotatedCredentialsOnTheNextCall() {
        AuthInterceptor interceptor = new AuthInterceptor("admin", "123456");
        assertEquals("123456", startCall(interceptor).get(PASSWORD_KEY));

        interceptor.updateCredentials("admin", "rotated");

        Metadata headers = startCall(interceptor);
        assertEquals("admin", headers.get(USERNAME_KEY));
        assertEquals("rotated", headers.get(PASSWORD_KEY));
    }

    @Test
    public void shouldSendNoCredentialsWhenUnset() {
        Metadata headers = startCall(new AuthInterceptor(null, null));

        assertFalse(headers.containsKey(USERNAME_KEY));
        assertFalse(headers.containsKey(PASSWORD_KEY));
    }

    /**
     * A half-populated pair is never put on the wire, so a truncated secrets file cannot turn into a
     * request that authenticates as the right user with the wrong password.
     */
    @Test
    public void shouldSendNoCredentialsWhenOnlyOneHalfIsSet() {
        assertNull(startCall(new AuthInterceptor("admin", "  ")).get(USERNAME_KEY));
        assertNull(startCall(new AuthInterceptor("", "123456")).get(PASSWORD_KEY));
    }

    @Test
    public void shouldStopSendingCredentialsWhenClearedAtRuntime() {
        AuthInterceptor interceptor = new AuthInterceptor("admin", "123456");
        interceptor.updateCredentials(null, null);

        assertFalse(startCall(interceptor).containsKey(USERNAME_KEY));
    }

    @SuppressWarnings("unchecked")
    private Metadata startCall(AuthInterceptor interceptor) {
        Channel next = Mockito.mock(Channel.class);
        Mockito.doReturn(Mockito.mock(ClientCall.class))
               .when(next).newCall(Mockito.any(), Mockito.any());

        Metadata headers = new Metadata();
        interceptor.interceptCall(FakeMethodDescriptor.<String, Integer>create(), CallOptions.DEFAULT, next)
                   .start(Mockito.mock(ClientCall.Listener.class), headers);
        return headers;
    }
}
