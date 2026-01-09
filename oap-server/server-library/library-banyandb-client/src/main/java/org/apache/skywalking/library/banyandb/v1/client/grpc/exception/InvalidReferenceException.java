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

public class InvalidReferenceException extends BanyanDBException {
    private final String refName;

    private InvalidReferenceException(String refName, String message) {
        super(message, null, Status.Code.INVALID_ARGUMENT, false);
        this.refName = refName;
    }

    public static InvalidReferenceException fromInvalidTag(String tagName) {
        return new InvalidReferenceException(tagName, "invalid ref to tag " + tagName);
    }

    public static InvalidReferenceException fromInvalidField(String fieldName) {
        return new InvalidReferenceException(fieldName, "invalid ref to field " + fieldName);
    }
}
