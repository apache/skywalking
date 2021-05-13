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

package org.apache.skywalking.oap.server.core.analysis.metrics.expression;

import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.FilterMatcher;

@FilterMatcher
public class LikeMatch {
    public boolean match(String left, String right) {
        if (right == null || left == null) {
            return false;
        }
        if (right.startsWith("\"") && right.endsWith("\"")) {
            right = right.substring(1, right.length() - 1);
        }

        if (right.startsWith("%") && right.endsWith("%")) { // %keyword%
            return left.contains(right.substring(1, right.length() - 1));
        }
        return (right.startsWith("%") && left.endsWith(right.substring(1)))  // %suffix
            || (right.endsWith("%") && left.startsWith(right.substring(0, right.length() - 1))) // prefix%
            ;
    }
}
