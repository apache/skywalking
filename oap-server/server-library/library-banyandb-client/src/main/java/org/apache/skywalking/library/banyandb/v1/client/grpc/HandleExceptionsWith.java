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

package org.apache.skywalking.library.banyandb.v1.client.grpc;

import com.google.common.collect.Sets;
import io.grpc.Status;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.BanyanDBException;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.BanyanDBGrpcApiExceptionFactory;

public class HandleExceptionsWith {
    private HandleExceptionsWith() {
    }

    private static final BanyanDBGrpcApiExceptionFactory EXCEPTION_FACTORY = new BanyanDBGrpcApiExceptionFactory(
            // Exceptions caused by network issues are retryable
            Sets.newHashSet(Status.Code.UNAVAILABLE, Status.Code.DEADLINE_EXCEEDED)
    );

    /**
     * call the underlying operation and get response from the future.
     *
     * @param respSupplier a supplier which returns response
     * @param <RESP>       a generic type of user-defined gRPC response
     * @return response in the type of  defined in the gRPC protocol
     * @throws BanyanDBException if the execution of the future itself thrown an exception
     */
    public static <RESP, E extends BanyanDBException> RESP callAndTranslateApiException(SupplierWithIO<RESP, E> respSupplier) throws BanyanDBException {
        try {
            return respSupplier.get();
        } catch (Exception exception) {
            throw EXCEPTION_FACTORY.createException(exception);
        }
    }

    @FunctionalInterface
    public interface SupplierWithIO<T, E extends Throwable> {
        T get() throws E;
    }
}
