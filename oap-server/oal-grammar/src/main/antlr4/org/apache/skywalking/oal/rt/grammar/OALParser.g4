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
    : FROM LR_BRACKET (sourceAttrCast)? source (sourceAttributeStmt+) RR_BRACKET (filterStatement+)? DOT aggregateFunction (decorateSource)?
    ;

filterStatement
    : DOT FILTER LR_BRACKET filterExpression RR_BRACKET
    ;

filterExpression
    : expression
    ;

decorateSource
    : DOT DECORATOR LR_BRACKET STRING_LITERAL RR_BRACKET
    ;

source
    : SRC_SERVICE | SRC_TCP_SERVICE | SRC_DATABASE_ACCESS | SRC_SERVICE_INSTANCE | SRC_TCP_SERVICE_INSTANCE | SRC_ENDPOINT | SRC_CACHE_ACCESS |
      SRC_SERVICE_RELATION | SRC_TCP_SERVICE_RELATION | SRC_SERVICE_INSTANCE_RELATION | SRC_TCP_SERVICE_INSTANCE_RELATION | SRC_ENDPOINT_RELATION |
      SRC_SERVICE_INSTANCE_CLR_CPU | SRC_SERVICE_INSTANCE_CLR_GC | SRC_SERVICE_INSTANCE_CLR_THREAD |
      SRC_SERVICE_INSTANCE_JVM_CPU | SRC_SERVICE_INSTANCE_JVM_MEMORY | SRC_SERVICE_INSTANCE_JVM_MEMORY_POOL | SRC_SERVICE_INSTANCE_JVM_GC | SRC_SERVICE_INSTANCE_JVM_THREAD | SRC_SERVICE_INSTANCE_JVM_CLASS |// JVM source of service instance
      SRC_ENVOY_INSTANCE_METRIC |
      SRC_BROWSER_APP_PERF | SRC_BROWSER_APP_PAGE_PERF | SRC_BROWSER_APP_SINGLE_VERSION_PERF | SRC_BROWSER_APP_RESOURCE_PERF | SRC_BROWSER_APP_WEB_VITALS_PERF |
      SRC_BROWSER_APP_TRAFFIC | SRC_BROWSER_APP_PAGE_TRAFFIC | SRC_BROWSER_APP_SINGLE_VERSION_TRAFFIC |
      SRC_EVENT | SRC_MQ_ACCESS | SRC_MQ_ENDPOINT_ACCESS |
      SRC_K8S_SERVICE | SRC_K8S_SERVICE_INSTANCE | SRC_K8S_ENDPOINT | SRC_K8S_SERVICE_RELATION | SRC_K8S_SERVICE_INSTANCE_RELATION |
      SRC_CILIUM_SERVICE | SRC_CILIUM_SERVICE_INSTANCE | SRC_CILIUM_ENDPOINT | SRC_CILIUM_SERVICE_RELATION | SRC_CILIUM_SERVICE_INSTANCE_RELATION | SRC_CILIUM_ENDPOINT_RELATION
    ;

disableSource
    : IDENTIFIER
    ;

sourceAttributeStmt
    : DOT sourceAttribute
    ;

sourceAttribute
    : IDENTIFIER | ALL | mapAttribute
    ;

variable
    : IDENTIFIER
    ;

aggregateFunction
    : functionName LR_BRACKET ((funcParamExpression|literalExpression|attributeExpression) (COMMA (funcParamExpression|literalExpression|attributeExpression))?)? RR_BRACKET
    ;

functionName
    : IDENTIFIER
    ;

funcParamExpression
    : expression
    ;

literalExpression
    : BOOL_LITERAL | NUMBER_LITERAL | STRING_LITERAL
    ;

attributeExpression
    : functionArgCast? attributeExpressionSegment (DOT attributeExpressionSegment)*
    ;

attributeExpressionSegment
    : (IDENTIFIER | mapAttribute)
    ;

expression
    : booleanMatch | numberMatch | stringMatch | greaterMatch | lessMatch | greaterEqualMatch | lessEqualMatch | notEqualMatch | booleanNotEqualMatch | likeMatch | inMatch | containMatch | notContainMatch
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

numberMatch
    : conditionAttributeStmt DUALEQUALS numberConditionValue
    ;

stringMatch
    :  conditionAttributeStmt DUALEQUALS (stringConditionValue | enumConditionValue | nullConditionValue)
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
    :  conditionAttributeStmt NOT_EQUAL (numberConditionValue | stringConditionValue | enumConditionValue | nullConditionValue)
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
    : (expressionAttrCast)? conditionAttribute ((DOT conditionAttribute)*)
    ;

conditionAttribute
    : (IDENTIFIER | mapAttribute)
    ;

mapAttribute
    : IDENTIFIER LS_BRACKET STRING_LITERAL RS_BRACKET
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

nullConditionValue
    : NULL_LITERAL
    ;

sourceAttrCast
    : castStmt
    ;

expressionAttrCast
    : castStmt
    ;

functionArgCast
    : castStmt
    ;

castStmt
    : STRING_TO_LONG | STRING_TO_LONG_SHORT | STRING_TO_INT | STRING_TO_INT_SHORT
    ;
