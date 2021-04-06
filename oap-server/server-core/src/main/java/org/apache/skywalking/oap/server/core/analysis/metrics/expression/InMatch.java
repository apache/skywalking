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

package org.apache.skywalking.oap.server.core.analysis.metrics.expression;

import java.util.Objects;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.FilterMatcher;

@FilterMatcher
public class InMatch {

    public boolean match(int left, long[] rights) {
        for (long right : rights) {
            if (left == right) {
                return true;
            }
        }
        return false;
    }

    public boolean match(long left, long[] rights) {
        for (long right : rights) {
            if (left == right) {
                return true;
            }
        }
        return false;
    }

    public boolean match(Object left, Object[] rights) {
        for (Object right : rights) {
            if (right instanceof String) {
                String r = (String) right;
                if (r.startsWith("\"") && r.endsWith("\"")) {
                    right = r.substring(1, r.length() - 1);
                }
            }
            if (Objects.equals(left, right)) {
                return true;
            }
        }
        return false;
    }
}
