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
    : (aggregationStatement)*
    ;

aggregationStatement
    : variable (SPACE)? EQUAL (SPACE)? metricStatement DelimitedComment? LineComment? (SEMI|EOF)
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
      SRC_SERVICE_INSTANCE_JVM_CPU | SRC_SERVICE_INSTANCE_JVM_MEMORY | SRC_SERVICE_INSTANCE_JVM_MEMORY_POOL | SRC_SERVICE_INSTANCE_JVM_GC// JVM source of service instance
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
    : BOOL_LITERAL | INT_LITERAL
    ;

expression
    : booleanMatch | stringMatch
    ;

booleanMatch
    :  conditionAttribute DUALEQUALS booleanConditionValue
    ;

stringMatch
    :  conditionAttribute DUALEQUALS (stringConditionValue | enumConditionValue)
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