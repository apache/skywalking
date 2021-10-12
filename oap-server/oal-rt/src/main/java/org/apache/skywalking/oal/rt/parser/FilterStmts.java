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

package org.apache.skywalking.oal.rt.parser;

import java.util.LinkedList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Filter statements in the OAL scripts.
 */
@Getter
@Setter
public class FilterStmts {
    /**
     * Parsed raw result from grammar tree.
     */
    private List<ConditionExpression> filterExpressionsParserResult;
    /**
     * Generated expressions for code generation.
     */
    private List<Expression> filterExpressions;

    public void addFilterExpressions(Expression filterExpression) {
        if (filterExpressions == null) {
            filterExpressions = new LinkedList<>();
        }
        filterExpressions.add(filterExpression);
    }

    public void addFilterExpressionsParserResult(ConditionExpression conditionExpression) {
        if (filterExpressionsParserResult == null) {
            filterExpressionsParserResult = new LinkedList<>();
        }
        filterExpressionsParserResult.add(conditionExpression);
    }
}
