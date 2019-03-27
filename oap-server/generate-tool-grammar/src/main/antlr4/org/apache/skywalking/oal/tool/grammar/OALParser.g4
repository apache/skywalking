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

@Header {package org.apache.skywalking.oal.tool.grammar;}

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
    : FROM LR_BRACKET source  DOT sourceAttribute RR_BRACKET (filterStatement+)? DOT aggregateFunction
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
      SRC_SERVICE_INSTANCE_JVM_CPU | SRC_SERVICE_INSTANCE_JVM_MEMORY | SRC_SERVICE_INSTANCE_JVM_MEMORY_POOL | SRC_SERVICE_INSTANCE_JVM_GC |// JVM source of service instance
      SRC_SERVICE_INSTANCE_CLR_CPU | SRC_SERVICE_INSTANCE_CLR_GC | SRC_SERVICE_INSTANCE_CLR_THREAD |
      SRC_ENVOY_INSTANCE_METRIC
    ;

disableSource
    : SRC_SEGMENT | SRC_TOP_N_DB_STATEMENT | SRC_ENDPOINT_RELATION_SERVER_SIDE | SRC_SERVICE_RELATION_SERVER_SIDE |
      SRC_SERVICE_RELATION_CLIENT_SIDE | SRC_ALARM_RECORD
    ;

sourceAttribute
    : IDENTIFIER | ALL
    ;

variable
    : IDENTIFIER
    ;

aggregateFunction
    : functionName LR_BRACKET (funcParamExpression | (literalExpression (COMMA literalExpression)?))? RR_BRACKET
    ;

functionName
    : IDENTIFIER
    ;

funcParamExpression
    : expression
    ;

literalExpression
    : BOOL_LITERAL | NUMBER_LITERAL
    ;

expression
    : booleanMatch | stringMatch | greaterMatch | lessMatch | greaterEqualMatch | lessEqualMatch
    ;

booleanMatch
    :  conditionAttribute DUALEQUALS booleanConditionValue
    ;

stringMatch
    :  conditionAttribute DUALEQUALS (stringConditionValue | enumConditionValue)
    ;

greaterMatch
    :  conditionAttribute GREATER numberConditionValue
    ;

lessMatch
    :  conditionAttribute LESS numberConditionValue
    ;

greaterEqualMatch
    :  conditionAttribute GREATER_EQUAL numberConditionValue
    ;

lessEqualMatch
    :  conditionAttribute LESS_EQUAL numberConditionValue
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