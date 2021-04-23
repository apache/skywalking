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

import java.util.List;

public class ClassMethodUtil {
    public static String toGetMethod(String attribute) {
        return "get" + attribute.substring(0, 1).toUpperCase() + attribute.substring(1);
    }

    public static String toSetMethod(String attribute) {
        return "set" + attribute.substring(0, 1).toUpperCase() + attribute.substring(1);
    }

    public static String toIsMethod(String attribute) {
        return "is" + attribute.substring(0, 1).toUpperCase() + attribute.substring(1);
    }

    /**
     * @return nested get methods.
     */
    public static String toGetMethod(List<String> attributes) {
        StringBuilder method = new StringBuilder();
        for (int i = 0; i < attributes.size(); i++) {
            if (i != 0) {
                method.append(".");
            }
            if (i != attributes.size() - 1) {
                method.append(toGetMethod(attributes.get(i))).append("()");
            } else {
                method.append(toGetMethod(attributes.get(i)));
            }
        }
        return method.toString();
    }

    /**
     * @return nested get/is methods.
     */
    public static String toIsMethod(List<String> attributes) {
        StringBuilder method = new StringBuilder();
        for (int i = 0; i < attributes.size(); i++) {
            if (i != 0) {
                method.append(".");
            }
            if (i != attributes.size() - 1) {
                method.append(toGetMethod(attributes.get(i))).append("()");
            } else {
                method.append(toIsMethod(attributes.get(i)));
            }
        }
        return method.toString();
    }
}
