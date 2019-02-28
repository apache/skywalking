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

package org.apache.skywalking.apm.util;

import java.util.HashMap;
import java.util.Map;

/**
 * @Author: zhaoyuguang
 * @Date: 2019/2/27 9:05 PM
 */

public class ClassUtil {

    private static final Map<String, Class> PRIMITIVE = new HashMap<String, Class>();

    static {
        PRIMITIVE.put("boolean.class", boolean.class);
        PRIMITIVE.put("char.class", char.class);
        PRIMITIVE.put("byte.class", byte.class);
        PRIMITIVE.put("short.class", short.class);
        PRIMITIVE.put("int.class", int.class);
        PRIMITIVE.put("long.class", long.class);
        PRIMITIVE.put("float.class", float.class);
        PRIMITIVE.put("double.class", double.class);
    }

    /**
     * Extend the Class.forName {@link Class} method,
     * which supports Java PrimitiveType, for example: "int.class"
     * return Class int.class
     *
     * @param className the fully qualified name of the desired class.
     * @return the {@code Class} object for the class with the specified name.
     * @throws ClassNotFoundException if the class cannot be located
     */
    public static Class forName(String className) throws ClassNotFoundException {
        return PRIMITIVE.containsKey(className) ? PRIMITIVE.get(className) : Class.forName(className);
    }

}
