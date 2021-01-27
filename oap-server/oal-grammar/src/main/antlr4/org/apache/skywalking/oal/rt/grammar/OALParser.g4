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

parser grammar OALParser;

@Header {package org.apache.skywalking.oal.rt.grammar;}

options { tokenVocab=OALLexer; }


// Top Level Description

root
    : (aggregationStatement | disableStatement)*
    ;

aggregationStatement
    : variable (SPACE)? EQUAL (SPACE)? metricStatement DelimitedComment? LineComment? (SEMI|EOF)
    ;

disableStatement
    : DISABLE LR_BRACKET disableSource RR_BRACKET DelimitedComment? LineComment? (SEMI|EOF)
    ;

metricStatement
    : FROM LR_BRACKET source (sourceAttributeStmt+) RR_BRACKET (filterStatement+)? DOT aggregateFunction
    ;

filterStatement
    : DOT FILTER LR_BRACKET filterExpression RR_BRACKET
    ;

filterExpression
    : expression
    ;

source
    : SRC_ALL | SRC_SERVICE | SRC_DATABASE_ACCESS | SRC_SERVICE_INSTANCE | SRC_ENDPOINT |
      SRC_SERVICE_RELATION | SRC_SERVICE_INSTANCE_RELATION | SRC_ENDPOINT_RELATION |
      SRC_SERVICE_INSTANCE_JVM_CPU | SRC_SERVICE_INSTANCE_JVM_MEMORY | SRC_SERVICE_INSTANCE_JVM_MEMORY_POOL | SRC_SERVICE_INSTANCE_JVM_GC | SRC_SERVICE_INSTANCE_JVM_THREAD |// JVM source of service instance
      SRC_SERVICE_INSTANCE_CLR_CPU | SRC_SERVICE_INSTANCE_CLR_GC | SRC_SERVICE_INSTANCE_CLR_THREAD |
      SRC_ENVOY_INSTANCE_METRIC |
      SRC_BROWSER_APP_PERF | SRC_BROWSER_APP_PAGE_PERF | SRC_BROWSER_APP_SINGLE_VERSION_PERF |
      SRC_BROWSER_APP_TRAFFIC | SRC_BROWSER_APP_PAGE_TRAFFIC | SRC_BROWSER_APP_SINGLE_VERSION_TRAFFIC
    ;

disableSource
    : IDENTIFIER
    ;

sourceAttributeStmt
    : DOT sourceAttribute
    ;

sourceAttribute
    : IDENTIFIER | ALL
    ;

variable
    : IDENTIFIER
    ;

aggregateFunction
    : functionName LR_BRACKET ((funcParamExpression (COMMA funcParamExpression)?) | (literalExpression (COMMA literalExpression)?))? RR_BRACKET
    ;

functionName
    : IDENTIFIER
    ;

funcParamExpression
    : expression
    ;

literalExpression
    : BOOL_LITERAL | NUMBER_LITERAL | IDENTIFIER
    ;

expression
    : booleanMatch | stringMatch | greaterMatch | lessMatch | greaterEqualMatch | lessEqualMatch | notEqualMatch | booleanNotEqualMatch | likeMatch | inMatch | containMatch | notContainMatch
    ;

containMatch
    : conditionAttributeStmt CONTAIN stringConditionValue
    ;

notContainMatch
    : conditionAttributeStmt NOT_CONTAIN stringConditionValue
    ;

booleanMatch
    : conditionAttributeStmt DUALEQUALS booleanConditionValue
    ;

stringMatch
    :  conditionAttributeStmt DUALEQUALS (stringConditionValue | enumConditionValue)
    ;

greaterMatch
    :  conditionAttributeStmt GREATER numberConditionValue
    ;

lessMatch
    :  conditionAttributeStmt LESS numberConditionValue
    ;

greaterEqualMatch
    :  conditionAttributeStmt GREATER_EQUAL numberConditionValue
    ;

lessEqualMatch
    :  conditionAttributeStmt LESS_EQUAL numberConditionValue
    ;

booleanNotEqualMatch
    :  conditionAttributeStmt NOT_EQUAL booleanConditionValue
    ;

notEqualMatch
    :  conditionAttributeStmt NOT_EQUAL (numberConditionValue | stringConditionValue | enumConditionValue)
    ;

likeMatch
    :  conditionAttributeStmt LIKE stringConditionValue
    ;

inMatch
    :  conditionAttributeStmt IN multiConditionValue
    ;

multiConditionValue
    : LS_BRACKET (numberConditionValue ((COMMA numberConditionValue)*) | stringConditionValue ((COMMA stringConditionValue)*) | enumConditionValue ((COMMA enumConditionValue)*)) RS_BRACKET
    ;

conditionAttributeStmt
    : conditionAttribute ((DOT conditionAttribute)*)
    ;

conditionAttribute
    : IDENTIFIER
    ;

booleanConditionValue
    : BOOL_LITERAL
    ;

stringConditionValue
    : STRING_LITERAL
    ;

enumConditionValue
    : IDENTIFIER DOT IDENTIFIER
    ;

numberConditionValue
    : NUMBER_LITERAL
    ;
