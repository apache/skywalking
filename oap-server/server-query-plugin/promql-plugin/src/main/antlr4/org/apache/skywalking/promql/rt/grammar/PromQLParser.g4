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

parser grammar PromQLParser;

options { tokenVocab = PromQLLexer; }

root: expression EOF;

expression
    : expressionNode                   # exprNode
    | expression mulDivMod expression  # mulDivModOp
    | expression addSub expression     # addSubOp
    | expression compare expression    # compareOp
    | aggregationFunc (aggregationClause)? L_PAREN expression R_PAREN   # aggregationOp
    | aggregationFunc L_PAREN expression R_PAREN (aggregationClause)?   # aggregationOp
    ;

expressionNode:  metricInstant| metricRange| numberLiteral| badRange;

addSub:          ADD | SUB ;
mulDivMod:       MUL | DIV | MOD;
compare:        (DEQ | NEQ | LTE | LT | GTE | GT) BOOL?;

aggregationFunc:
    AVG | SUM | MAX | MIN;

aggregationClause:
    (BY | WITHOUT) L_PAREN labelNameList R_PAREN;

metricName:      NAME_STRING;
metricInstant:   metricName | metricName L_BRACE labelList? R_BRACE;
metricRange:     metricInstant L_BRACKET DURATION R_BRACKET;

labelName:       NAME_STRING;
labelValue:      VALUE_STRING;
label:           labelName EQ labelValue;
labelList:       label (COMMA label)*;
labelNameList:   labelName (COMMA labelName)*;

numberLiteral:   NUMBER;

badRange:        NUMBER L_BRACKET DURATION R_BRACKET;

