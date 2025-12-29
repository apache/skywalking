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

import io.grpc.Status;

class BanyanDBApiExceptionFactory {
    private BanyanDBApiExceptionFactory() {
    }

    public static BanyanDBException createException(Throwable cause, Status.Code statusCode, boolean retryable) {
        switch (statusCode) {
            case CANCELLED:
                return new CancelledException(cause, statusCode, retryable);
            case NOT_FOUND:
                return new NotFoundException(cause, statusCode, retryable);
            case INVALID_ARGUMENT:
                return new InvalidArgumentException(cause, statusCode, retryable);
            case DEADLINE_EXCEEDED:
                return new DeadlineExceededException(cause, statusCode, retryable);
            case ALREADY_EXISTS:
                return new AlreadyExistsException(cause, statusCode, retryable);
            case PERMISSION_DENIED:
                return new PermissionDeniedException(cause, statusCode, retryable);
            case RESOURCE_EXHAUSTED:
                return new ResourceExhaustedException(cause, statusCode, retryable);
            case FAILED_PRECONDITION:
                return new FailedPreconditionException(cause, statusCode, retryable);
            case ABORTED:
                return new AbortedException(cause, statusCode, retryable);
            case OUT_OF_RANGE:
                return new OutOfRangeException(cause, statusCode, retryable);
            case UNIMPLEMENTED:
                return new UnimplementedException(cause, statusCode, retryable);
            case INTERNAL:
                return new InternalException(cause, statusCode, retryable);
            case UNAVAILABLE:
                return new UnavailableException(cause, statusCode, retryable);
            case DATA_LOSS:
                return new DataLossException(cause, statusCode, retryable);
            case UNAUTHENTICATED:
                return new UnauthenticatedException(cause, statusCode, retryable);
            case UNKNOWN: // Fall through.
            default:
                return new UnknownException(cause, statusCode, retryable);
        }
    }
}