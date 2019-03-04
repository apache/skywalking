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
 * @author zhaoyuguang
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
     * Determine if classname is a class of Primitive type.
     *
     * @param className such as int.class.
     * @return Whether Primitive types of class.
     */
    public static boolean isPrimitive(String className) {
        return PRIMITIVE.containsKey(className);
    }

    /**
     * If the class name is of the Primitive type,
     * then return the class of the Primitive type.
     *
     * @param className such as int.class
     * @return class of the Primitive type
     */
    public static Class getPrimitiveClass(String className) {
        return PRIMITIVE.get(className);
    }

}
