/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.skywalking.apm.plugin.dubbo.patch;

import com.alibaba.dubbo.common.bytecode.ClassGenerator;
import com.alibaba.dubbo.common.bytecode.NoSuchPropertyException;
import com.alibaba.dubbo.common.bytecode.Wrapper;
import com.alibaba.dubbo.common.utils.ClassHelper;
import com.alibaba.dubbo.common.utils.ReflectUtils;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.StaticMethodsAroundInterceptor;

public class MakeWrapperInterceptor implements StaticMethodsAroundInterceptor {

    private static final AtomicLong WRAPPER_CLASS_COUNTER = new AtomicLong(0);

    @Override
    public void beforeMethod(Class clazz, Method method, Object[] allArguments, Class<?>[] parameterTypes,
        MethodInterceptResult result) {
        Class wrapperClass = (Class<?>) allArguments[0];
        if (EnhancedInstance.class.isAssignableFrom(wrapperClass)) {
            result.defineReturnValue(makeWrapper(wrapperClass));
        }
    }

    @Override
    public Object afterMethod(Class clazz, Method method, Object[] allArguments, Class<?>[] parameterTypes,
        Object ret) {
        return ret;
    }

    @Override
    public void handleMethodException(Class clazz, Method method, Object[] allArguments, Class<?>[] parameterTypes,
        Throwable t) {
    }

    private static Wrapper makeWrapper(Class<?> c) {
        if (c.isPrimitive())
            throw new IllegalArgumentException("Can not create wrapper for primitive type: " + c);

        String name = c.getName();
        ClassLoader cl = ClassHelper.getClassLoader(c);

        StringBuilder c1 = new StringBuilder("public void setPropertyValue(Object o, String n, Object v){ ");
        StringBuilder c2 = new StringBuilder("public Object getPropertyValue(Object o, String n){ ");
        StringBuilder c3 = new StringBuilder("public Object invokeMethod(Object o, String n, Class[] p, Object[] v) throws " + InvocationTargetException.class
            .getName() + "{ ");

        c1.append(name)
          .append(" w; try{ w = ((")
          .append(name)
          .append(")$1); }catch(Throwable e){ throw new IllegalArgumentException(e); }");
        c2.append(name)
          .append(" w; try{ w = ((")
          .append(name)
          .append(")$1); }catch(Throwable e){ throw new IllegalArgumentException(e); }");
        c3.append(name)
          .append(" w; try{ w = ((")
          .append(name)
          .append(")$1); }catch(Throwable e){ throw new IllegalArgumentException(e); }");

        Map<String, Class<?>> pts = new HashMap<String, Class<?>>(); // <property name, property types>
        Map<String, Method> ms = new LinkedHashMap<String, Method>(); // <method desc, Method instance>
        List<String> mns = new ArrayList<String>(); // method names.
        List<String> dmns = new ArrayList<String>(); // declaring method names.

        // get all public field.
        for (Field f : c.getFields()) {
            String fn = f.getName();
            Class<?> ft = f.getType();
            if (Modifier.isStatic(f.getModifiers()) || Modifier.isTransient(f.getModifiers()))
                continue;

            c1.append(" if( $2.equals(\"")
              .append(fn)
              .append("\") ){ w.")
              .append(fn)
              .append("=")
              .append(arg(ft, "$3"))
              .append("; return; }");
            c2.append(" if( $2.equals(\"").append(fn).append("\") ){ return ($w)w.").append(fn).append("; }");
            pts.put(fn, ft);
        }

        Method[] methods = c.getMethods();
        // get all public method.
        boolean hasMethod = hasMethods(methods);
        if (hasMethod) {
            c3.append(" try{");
        }
        for (Method m : methods) {
            // ignore Object's method
            // ignore EnhanceInstance's method
            if (m.getDeclaringClass() == Object.class || "getSkyWalkingDynamicField".equals(m.getName()) || "setSkyWalkingDynamicField"
                .equals(m.getName())) {
                continue;
            }

            String mn = m.getName();
            c3.append(" if( \"").append(mn).append("\".equals( $2 ) ");
            int len = m.getParameterTypes().length;
            c3.append(" && ").append(" $3.length == ").append(len);

            boolean override = false;
            for (Method m2 : methods) {
                if (!m.equals(m2) && m.getName().equals(m2.getName())) {
                    override = true;
                    break;
                }
            }
            if (override) {
                if (len > 0) {
                    for (int l = 0; l < len; l++) {
                        c3.append(" && ")
                          .append(" $3[")
                          .append(l)
                          .append("].getName().equals(\"")
                          .append(m.getParameterTypes()[l].getName())
                          .append("\")");
                    }
                }
            }

            c3.append(" ) { ");

            if (m.getReturnType() == Void.TYPE)
                c3.append(" w.")
                  .append(mn)
                  .append('(')
                  .append(args(m.getParameterTypes(), "$4"))
                  .append(");")
                  .append(" return null;");
            else
                c3.append(" return ($w)w.")
                  .append(mn)
                  .append('(')
                  .append(args(m.getParameterTypes(), "$4"))
                  .append(");");

            c3.append(" }");

            mns.add(mn);
            if (m.getDeclaringClass() == c)
                dmns.add(mn);
            ms.put(ReflectUtils.getDesc(m), m);
        }
        if (hasMethod) {
            c3.append(" } catch(Throwable e) { ");
            c3.append("     throw new java.lang.reflect.InvocationTargetException(e); ");
            c3.append(" }");
        }

        c3.append(" throw new " + NoSuchMethodException.class.getName() + "(\"Not found method \\\"\"+$2+\"\\\" in class " + c
            .getName() + ".\"); }");

        // deal with get/set method.
        Matcher matcher;
        for (Map.Entry<String, Method> entry : ms.entrySet()) {
            String md = entry.getKey();
            Method method = (Method) entry.getValue();
            if ((matcher = ReflectUtils.GETTER_METHOD_DESC_PATTERN.matcher(md)).matches()) {
                String pn = propertyName(matcher.group(1));
                c2.append(" if( $2.equals(\"")
                  .append(pn)
                  .append("\") ){ return ($w)w.")
                  .append(method.getName())
                  .append("(); }");
                pts.put(pn, method.getReturnType());
            } else if ((matcher = ReflectUtils.IS_HAS_CAN_METHOD_DESC_PATTERN.matcher(md)).matches()) {
                String pn = propertyName(matcher.group(1));
                c2.append(" if( $2.equals(\"")
                  .append(pn)
                  .append("\") ){ return ($w)w.")
                  .append(method.getName())
                  .append("(); }");
                pts.put(pn, method.getReturnType());
            } else if ((matcher = ReflectUtils.SETTER_METHOD_DESC_PATTERN.matcher(md)).matches()) {
                Class<?> pt = method.getParameterTypes()[0];
                String pn = propertyName(matcher.group(1));
                c1.append(" if( $2.equals(\"")
                  .append(pn)
                  .append("\") ){ w.")
                  .append(method.getName())
                  .append("(")
                  .append(arg(pt, "$3"))
                  .append("); return; }");
                pts.put(pn, pt);
            }
        }
        c1.append(" throw new " + NoSuchPropertyException.class.getName() + "(\"Not found property \\\"\"+$2+\"\\\" filed or setter method in class " + c
            .getName() + ".\"); }");
        c2.append(" throw new " + NoSuchPropertyException.class.getName() + "(\"Not found property \\\"\"+$2+\"\\\" filed or setter method in class " + c
            .getName() + ".\"); }");

        // make class
        long id = WRAPPER_CLASS_COUNTER.getAndIncrement();
        ClassGenerator cc = ClassGenerator.newInstance(cl);
        cc.setClassName((Modifier.isPublic(c.getModifiers()) ? Wrapper.class.getName() + "_swEnhance" : c.getName() + "$sw$_swEnhance") + id);
        cc.setSuperClass(Wrapper.class);

        cc.addDefaultConstructor();
        cc.addField("public static String[] pns;"); // property name array.
        cc.addField("public static " + Map.class.getName() + " pts;"); // property type map.
        cc.addField("public static String[] mns;"); // all method name array.
        cc.addField("public static String[] dmns;"); // declared method name array.
        for (int i = 0, len = ms.size(); i < len; i++)
            cc.addField("public static Class[] mts" + i + ";");

        cc.addMethod("public String[] getPropertyNames(){ return pns; }");
        cc.addMethod("public boolean hasProperty(String n){ return pts.containsKey($1); }");
        cc.addMethod("public Class getPropertyType(String n){ return (Class)pts.get($1); }");
        cc.addMethod("public String[] getMethodNames(){ return mns; }");
        cc.addMethod("public String[] getDeclaredMethodNames(){ return dmns; }");
        cc.addMethod(c1.toString());
        cc.addMethod(c2.toString());
        cc.addMethod(c3.toString());

        try {
            Class<?> wc = cc.toClass();
            // setup static field.
            wc.getField("pts").set(null, pts);
            wc.getField("pns").set(null, pts.keySet().toArray(new String[0]));
            wc.getField("mns").set(null, mns.toArray(new String[0]));
            wc.getField("dmns").set(null, dmns.toArray(new String[0]));
            int ix = 0;
            for (Method m : ms.values())
                wc.getField("mts" + ix++).set(null, m.getParameterTypes());
            return (Wrapper) wc.getDeclaredConstructor().newInstance();
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            cc.release();
            ms.clear();
            mns.clear();
            dmns.clear();
        }
    }

    private static String args(Class<?>[] cs, String name) {
        int len = cs.length;
        if (len == 0)
            return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            if (i > 0)
                sb.append(',');
            sb.append(arg(cs[i], name + "[" + i + "]"));
        }
        return sb.toString();
    }

    private static String propertyName(String pn) {
        return pn.length() == 1 || Character.isLowerCase(pn.charAt(1)) ? Character.toLowerCase(pn.charAt(0)) + pn.substring(1) : pn;
    }

    private static boolean hasMethods(Method[] methods) {
        if (methods == null || methods.length == 0) {
            return false;
        }
        for (Method m : methods) {
            if (m.getDeclaringClass() != Object.class && !"getSkyWalkingDynamicField".equals(m.getName()) && !"setSkyWalkingDynamicField"
                .equals(m.getName())) {
                return true;
            }
        }
        return false;
    }

    private static String arg(Class<?> cl, String name) {
        if (cl.isPrimitive()) {
            if (cl == Boolean.TYPE)
                return "((Boolean)" + name + ").booleanValue()";
            if (cl == Byte.TYPE)
                return "((Byte)" + name + ").byteValue()";
            if (cl == Character.TYPE)
                return "((Character)" + name + ").charValue()";
            if (cl == Double.TYPE)
                return "((Number)" + name + ").doubleValue()";
            if (cl == Float.TYPE)
                return "((Number)" + name + ").floatValue()";
            if (cl == Integer.TYPE)
                return "((Number)" + name + ").intValue()";
            if (cl == Long.TYPE)
                return "((Number)" + name + ").longValue()";
            if (cl == Short.TYPE)
                return "((Number)" + name + ").shortValue()";
            throw new RuntimeException("Unknown primitive type: " + cl.getName());
        }
        return "(" + ReflectUtils.getName(cl) + ")" + name;
    }

}
