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

package org.apache.skywalking.library.banyandb.v1.client.grpc.exception;

import com.google.common.collect.ImmutableSet;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import java.util.Set;

public class BanyanDBGrpcApiExceptionFactory {
    private final ImmutableSet<Status.Code> retryableCodes;

    public BanyanDBGrpcApiExceptionFactory(Set<Status.Code> retryCodes) {
        this.retryableCodes = ImmutableSet.copyOf(retryCodes);
    }

    public BanyanDBException createException(Throwable throwable) {
        if (throwable instanceof StatusException) {
            StatusException e = (StatusException) throwable;
            return create(throwable, e.getStatus().getCode());
        } else if (throwable instanceof StatusRuntimeException) {
            StatusRuntimeException e = (StatusRuntimeException) throwable;
            return create(throwable, e.getStatus().getCode());
        } else if (throwable instanceof BanyanDBException) {
            return (BanyanDBException) throwable;
        } else {
            // Do not retry on unknown throwable, even when UNKNOWN is in retryableCodes
            return BanyanDBApiExceptionFactory.createException(
                    throwable, Status.Code.UNKNOWN, false);
        }
    }

    private BanyanDBException create(Throwable throwable, Status.Code statusCode) {
        boolean retryable = retryableCodes.contains(statusCode);
        return BanyanDBApiExceptionFactory.createException(throwable, statusCode, retryable);
    }
}