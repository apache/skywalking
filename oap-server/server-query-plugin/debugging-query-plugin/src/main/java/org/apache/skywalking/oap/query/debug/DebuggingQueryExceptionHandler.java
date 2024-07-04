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

package org.apache.skywalking.oap.query.debug;

import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.annotation.ExceptionHandlerFunction;
import lombok.extern.slf4j.Slf4j;

import static com.linecorp.armeria.common.HttpStatus.BAD_REQUEST;
import static com.linecorp.armeria.common.HttpStatus.INTERNAL_SERVER_ERROR;
import static com.linecorp.armeria.common.MediaType.ANY_TEXT_TYPE;

@Slf4j
public class DebuggingQueryExceptionHandler implements ExceptionHandlerFunction {
    @Override
    public HttpResponse handleException(final ServiceRequestContext ctx, final HttpRequest req, final Throwable cause) {
        String rspMsg = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
        // Response msg for illegal query args.
        if (cause instanceof IllegalArgumentException) {
            log.error(cause.getMessage(), cause);
            return HttpResponse.of(BAD_REQUEST, ANY_TEXT_TYPE, rspMsg);
        } else {
            log.error(cause.getMessage(), cause);
            return HttpResponse.of(INTERNAL_SERVER_ERROR, ANY_TEXT_TYPE, rspMsg);
        }
    }
}
