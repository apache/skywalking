package org.apache.skywalking.apm.plugin.customize.util;

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
 *
 * @author zhaoyuguang
 */

public class CustomizeExpression {

    private static final ILog logger = LogManager.getLogger(CustomizeExpression.class);

    public static Map<String, Object> evaluationContext(Object[] allArguments) {
        Map<String, Object> context = new HashMap<String, Object>();
        for (int i = 0; i < allArguments.length; i++) {
            context.put("arg[" + i + "]", allArguments[i]);
        }
        return context;
    }

    public static String parseExpression(String expression, Map<String, Object> context) {
        try {
            String[] es = expression.split("\\.");
            Object o = context.get(es[0]);
            return o == null ? "null" : String.valueOf(parse(es, o, 0));
        } catch (Exception e) {
            logger.error(e, "parse expression error, expression is {}", expression);
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
        int index = Integer.valueOf(expression.replace("[", "").replace("]", ""));
        return ((List) o).get(index);
    }

    private static Object matcherArray(String expression, Object o) {
        int index = Integer.valueOf(expression.replace("[", "").replace("]", ""));
        return Array.get(o, index);
    }

    private static Object matcherDefault(String expression, Object o) {
        try {
            if (expression.contains("()")) {
                Method m = o.getClass().getMethod(expression.replace("()", ""), null);
                return m.invoke(o, null);
            } else {
                Field f = o.getClass().getDeclaredField(expression);
                f.setAccessible(true);
                return f.get(o);
            }
        } catch (Exception e) {
            logger.error(e, "matcher default error, expression is {}, Object is {}", expression, o);
        }
        return null;
    }
}
