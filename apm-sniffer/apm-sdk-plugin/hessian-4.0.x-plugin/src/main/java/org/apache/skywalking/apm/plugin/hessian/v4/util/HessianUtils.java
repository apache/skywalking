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

package org.apache.skywalking.apm.plugin.hessian.v4.util;

import org.apache.skywalking.apm.util.StringUtil;

import static org.apache.skywalking.apm.plugin.hessian.v4.Constants.URI_AS_OPERATE_NAME;

/**
 * @author Alan Lau
 */
public class HessianUtils {

    /**
     * which kind format of the format the server side used.
     *
     * @return true: the interface class used, false uri used.  default false.
     */
    public static boolean getOperationNameLike() {
        String format = System.getProperty(URI_AS_OPERATE_NAME);
        if (StringUtil.isEmpty(format)) {
            return false;
        }

        return Boolean.valueOf(format);
    }
}
