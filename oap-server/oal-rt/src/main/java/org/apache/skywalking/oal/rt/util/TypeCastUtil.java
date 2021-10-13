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

package org.apache.skywalking.oal.rt.util;

public class TypeCastUtil {
    /**
     * @param castType           to change the value of given original expression.
     * @param originalExpression to read the value
     * @return cast expression if cast type exists and is legal.
     */
    public static String withCast(String castType, String originalExpression) {
        if (castType == null) {
            return originalExpression;
        }
        switch (castType) {
            case "(str->long)":
            case "(long)":
                return "Long.parseLong(" + originalExpression + ")";
            case "(str->int)":
            case "(int)":
                return "Integer.parseInt(" + originalExpression + ")";
            default:
                throw new IllegalArgumentException(
                    "castType:" + castType + " is legal, context expression:" + originalExpression);
        }
    }
}
