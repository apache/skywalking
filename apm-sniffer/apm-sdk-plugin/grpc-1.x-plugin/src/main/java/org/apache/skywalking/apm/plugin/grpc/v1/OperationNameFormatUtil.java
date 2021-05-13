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

package org.apache.skywalking.apm.plugin.grpc.v1;

import io.grpc.MethodDescriptor;

/**
 * Operation Name utility
 */
public class OperationNameFormatUtil {

    public static String formatOperationName(MethodDescriptor<?, ?> methodDescriptor) {
        String fullMethodName = methodDescriptor.getFullMethodName();
        return formatServiceName(fullMethodName) + "." + formatMethodName(fullMethodName);
    }

    private static String formatServiceName(String requestMethodName) {
        int splitIndex = requestMethodName.lastIndexOf("/");
        return requestMethodName.substring(0, splitIndex);
    }

    private static String formatMethodName(String requestMethodName) {
        int splitIndex = requestMethodName.lastIndexOf("/");
        String methodName = requestMethodName.substring(splitIndex + 1);
        methodName = methodName.substring(0, 1).toLowerCase() + methodName.substring(1);
        return methodName;
    }
}
