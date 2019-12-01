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

import org.apache.skywalking.e2e.assertor.exception.VariableNotFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * @author zhangwei
 */
public class VariableExpressParser {


    public static <T> T parse(String express, List<T> actual, Function<T, String> getFiled) {
        express = express.trim();
        if (!express.startsWith("${") && !express.endsWith("}")) {
            return null;
        }

        express = express.substring(2, express.length() - 1);

        int startIndexOfIndex = express.lastIndexOf("[");
        String regex = express.substring(0, startIndexOfIndex);
        int endIndexOfIndex = express.indexOf("]", startIndexOfIndex);
        int expectedIndex = Integer.parseInt(express.substring(startIndexOfIndex + 1, endIndexOfIndex));
        int expectedSize = expectedIndex + 1;

        List<T> mappings = new ArrayList<>(expectedSize);
        for (T t : actual) {
            if (Pattern.matches(regex, getFiled.apply(t))) {
                mappings.add(t);
                if (mappings.size() == expectedSize) {
                    break;
                }
            }
        }

        if (mappings.size() < expectedSize) {
            throw new VariableNotFoundException(express);
        }
        return mappings.get(expectedIndex);
    }
}
