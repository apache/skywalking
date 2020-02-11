/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.e2e.assertor;

import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.apache.skywalking.e2e.assertor.exception.VariableNotFoundException;

import static java.util.Objects.isNull;

public class VariableExpressParser {

    public static <T> T parse(final String express, List<T> actual, Function<T, String> getFiled) {
        String variable = express.trim();
        if (!(variable.startsWith("${") && variable.endsWith("}"))) {
            return null;
        }

        variable = variable.substring(2, variable.length() - 1);

        int startIndexOfIndex = variable.lastIndexOf("[");
        String regex = variable.substring(0, startIndexOfIndex);
        int endIndexOfIndex = variable.indexOf("]", startIndexOfIndex);
        int expectedIndex = Integer.parseInt(variable.substring(startIndexOfIndex + 1, endIndexOfIndex));
        int mappingIndex = 0;

        T mapping = null;
        for (T t : actual) {
            if (Pattern.matches(regex, getFiled.apply(t))) {
                if (mappingIndex++ == expectedIndex) {
                    mapping = t;
                    break;
                }
            }
        }

        if (isNull(mapping)) {
            throw new VariableNotFoundException(express);
        }
        return mapping;
    }
}
