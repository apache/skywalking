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

package org.apache.skywalking.apm.agent.core.util;

import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * a simple parsing expression
 */

public class CustomizeExpression {

    private static final ILog LOGGER = LogManager.getLogger(CustomizeExpression.class);

    public static Map<String, Object> evaluationContext(Object[] allArguments) {
        Map<String, Object> context = new HashMap<>();
        if (allArguments == null) {
            return context;
        }
        for (int i = 0; i < allArguments.length; i++) {
            context.put("arg[" + i + "]", allArguments[i]);
        }
        return context;
    }

    public static Map<String, Object> evaluationReturnContext(Object ret)  {
        Map<String, Object> context = new HashMap<>();
        context.put("returnedObj", ret.toString());
        if (ret instanceof List) {
            List retList = (List) ret;
            int retLength = retList.size();
            for (int i = 0; i < retLength; i++) {
                context.put(String.valueOf(i), retList.get(i));
            }
        } else if (ret.getClass().isArray()) {
            int length = Array.getLength(ret);
            for (int i = 0; i < length; i++) {
                context.put(String.valueOf(i), Array.get(ret, i));
            }
        } else if (ret instanceof Map) {
            context.putAll((Map) ret);
        } else {
            Field[] fields = ret.getClass().getDeclaredFields();
            for (Field field : fields) {
                field.setAccessible(true);
                try {
                    context.put(field.getName(), field.get(ret));
                } catch (Exception e) {
                    LOGGER.debug("evaluationReturnContext error, ret is {}, exception is {}", ret, e.getMessage());
                }
            }
        }
        return context;
    }

    public static String parseExpression(String expression, Map<String, Object> context) {
        try {
            String[] es = expression.split("\\.");
            Object o = context.get(es[0]);
            return o == null ? "null" : String.valueOf(parse(es, o, 0));
        } catch (Exception e) {
            LOGGER.debug("parse expression error, expression is {}, exception is {}", expression, e.getMessage());
        }
        return "null";
    }

    public static String parseReturnExpression(String expression, Map<String, Object> context) {
        try {
            String[] es = expression.split("\\.");
            if (es.length == 1) {
                return String.valueOf(context.get(es[0]));
            }
            Object o = context.get(es[1]);
            return o == null ? "null" : String.valueOf(parse(es, o, 1));
        } catch (Exception e) {
            LOGGER.debug("parse expression error, expression is {}, exception is {}", expression, e.getMessage());
        }
        return "null";
    }

    private static Object parse(String[] expressions, Object o, int i) {
        int next = i + 1;
        if (next == expressions.length) {
            return o;
        } else {
            o = parse0(expressions[next], o);
            return o == null ? "null" : parse(expressions, o, next);
        }
    }

    private static Object parse0(String expression, Object o) {
        if (o instanceof Map) {
            return matcherMap(expression, o);
        } else if (o instanceof List) {
            return matcherList(expression, o);
        } else if (o.getClass().isArray()) {
            return matcherArray(expression, o);
        } else {
            return matcherDefault(expression, o);
        }
    }

    private static Object matcherMap(String expression, Object o) {
        String key = expression.replace("['", "").replace("']", "");
        return ((Map) o).get(key);
    }

    private static Object matcherList(String expression, Object o) {
        int index = Integer.parseInt(expression.replace("[", "").replace("]", ""));
        List l = (List) o;
        return l != null && l.size() > index ? l.get(index) : null;
    }

    private static Object matcherArray(String expression, Object o) {
        int index = Integer.parseInt(expression.replace("[", "").replace("]", ""));
        return o != null && Array.getLength(o) > index ? Array.get(o, index) : null;
    }

    private static Object matcherDefault(String expression, Object o) {
        try {
            if (expression.contains("()")) {
                Method m = o.getClass().getMethod(expression.replace("()", ""));
                m.setAccessible(true);
                return m.invoke(o);
            } else {
                Field f = o.getClass().getDeclaredField(expression);
                f.setAccessible(true);
                return f.get(o);
            }
        } catch (Exception e) {
            LOGGER.debug("matcher default error, expression is {}, object is {}, expression is {}", expression, o, e.getMessage());
        }
        return null;
    }
}
