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

package org.apache.skywalking.apm.plugin.customize.conf;

import org.apache.skywalking.apm.plugin.customize.constants.CustomizeLanguage;

import java.lang.reflect.Method;

/**
 * Default custom enhancement configuration.
 *
 * @author zhaoyuguang
 */

public class DefaultMethodConfiguration {

    private Method method;
    private Class clz;
    private String operationName;
    private CustomizeLanguage language;
    private boolean closeBeforeMethod = false;
    private boolean closeAfterMethod = false;

    public Method getMethod() {
        return method;
    }

    public void setMethod(Method method) {
        this.method = method;
    }

    public Class getClz() {
        return clz;
    }

    public void setClz(Class clz) {
        this.clz = clz;
    }

    public String getOperationName() {
        return operationName;
    }

    public void setOperationName(String operationName) {
        this.operationName = operationName;
    }

    public CustomizeLanguage getLanguage() {
        return language;
    }

    public void setLanguage(CustomizeLanguage language) {
        this.language = language;
    }

    public boolean isCloseBeforeMethod() {
        return closeBeforeMethod;
    }

    public void setCloseBeforeMethod(boolean closeBeforeMethod) {
        this.closeBeforeMethod = closeBeforeMethod;
    }

    public boolean isCloseAfterMethod() {
        return closeAfterMethod;
    }

    public void setCloseAfterMethod(boolean closeAfterMethod) {
        this.closeAfterMethod = closeAfterMethod;
    }
}
