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
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.log4j;

import java.util.Hashtable;
import org.apache.log4j.helpers.ThreadLocalMap;

/**
 based on a map instead of a stack. It provides <em>mapped
 diagnostic contexts</em>. A <em>Mapped Diagnostic Context</em>, or
 MDC in short, is an instrument for distinguishing interleaved log
 output from different sources. Log output is typically interleaved
 when a server handles multiple clients near-simultaneously.

 <p><b><em>The MDC is managed on a per thread basis</em></b>. A
 child thread automatically inherits a <em>copy</em> of the mapped
 diagnostic context of its parent.

 <p>The MDC class requires JDK 1.2 or above. Under JDK 1.1 the MDC
 will always return empty values but otherwise will not affect or
 harm your application.

 @since 1.2

 @author Ceki G&uuml;lc&uuml; */
public class MDC {

    final static MDC mdc = new MDC();

    static final int HT_SIZE = 7;

    boolean java1;

    Object tlm;

    private MDC() {
//        java1 = Loader.isJava1();
        java1 = false;
        if (!java1) {
            tlm = new ThreadLocalMap();
        }
    }

    /**
     Put a context value (the <code>o</code> parameter) as identified
     with the <code>key</code> parameter into the current thread's
     context map.

     <p>If the current thread does not have a context map it is
     created as a side effect.

     */
    static public void put(String key, Object o) {
        if (mdc != null) {
            mdc.put0(key, o);
        }
    }

    /**
     Get the context identified by the <code>key</code> parameter.

     <p>This method has no side effects.
     */
    static public Object get(String key) {
        if (mdc != null) {
            return mdc.get0(key);
        }
        return null;
    }

    /**
     Remove the the context identified by the <code>key</code>
     parameter.

     */
    static public void remove(String key) {
        if (mdc != null) {
            mdc.remove0(key);
        }
    }


    /**
     * Get the current thread's MDC as a hashtable. This method is
     * intended to be used internally.
     * */
    public static Hashtable getContext() {
        if (mdc != null) {
            return mdc.getContext0();
        } else {
            return null;
        }
    }

    /**
     *  Remove all values from the MDC.
     *  @since 1.2.16
     */
    public static void clear() {
        if (mdc != null) {
            mdc.clear0();
        }
    }


    private void put0(String key, Object o) {
        if (java1 || tlm == null) {
            return;
        } else {
            Hashtable ht = (Hashtable) ((ThreadLocalMap)tlm).get();
            if (ht == null) {
                ht = new Hashtable(HT_SIZE);
                ((ThreadLocalMap)tlm).set(ht);
            }
            ht.put(key, o);
        }
    }

    private Object get0(String key) {
        if (java1 || tlm == null) {
            return null;
        } else {
            Hashtable ht = (Hashtable) ((ThreadLocalMap)tlm).get();
            if (ht != null && key != null) {
                return ht.get(key);
            } else {
                return null;
            }
        }
    }

    private void remove0(String key) {
        if (!java1 && tlm != null) {
            Hashtable ht = (Hashtable) ((ThreadLocalMap)tlm).get();
            if (ht != null) {
                ht.remove(key);
            }
        }
    }


    private Hashtable getContext0() {
        if (java1 || tlm == null) {
            return null;
        } else {
            return (Hashtable) ((ThreadLocalMap)tlm).get();
        }
    }

    private void clear0() {
        if (!java1 && tlm != null) {
            Hashtable ht = (Hashtable) ((ThreadLocalMap)tlm).get();
            if (ht != null) {
                ht.clear();
            }
        }
    }

}
