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

parser grammar MQEParser;

options { tokenVocab = MQELexer; }

root: expression;

expression
    : expressionNode                   # exprNode
    | expression mulDivMod expression  # mulDivModOp
    | expression addSub expression     # addSubOp
    | expression compare expression    # compareOp
    | aggregation L_PAREN expression R_PAREN # aggregationOp
    | function0 L_PAREN expression R_PAREN #function0OP
    | function1 L_PAREN expression COMMA parameter R_PAREN #function1OP
    | topN L_PAREN metric COMMA parameter COMMA order R_PAREN  #topNOP
    | relabels L_PAREN expression COMMA label R_PAREN #relablesOP
    | aggregateLabels L_PAREN expression COMMA aggregateLabelsFunc R_PAREN #aggregateLabelsOp
    | viewAsSeq L_PAREN expressionList R_PAREN #viewAsSeqOp
    ;

expressionList
    : expression (COMMA expression)*
    ;

expressionNode:  metric| scalar;

addSub:          ADD | SUB ;
mulDivMod:       MUL | DIV | MOD;
compare:        (DEQ | NEQ | LTE | LT | GTE | GT) BOOL?;

metricName:      NAME_STRING;
metric:   metricName | metricName L_BRACE label? R_BRACE;

//For now, we only have a single anonymous label with multi label values in a labeled metric.
//To be able to use it in expressions, define `_` as the anonymous label name (key).
labelName:       GENERAL_LABEL_NAME;
labelValue:      VALUE_STRING;
label:           labelName EQ labelValue;


scalar:   INTEGER | DECIMAL;

aggregation:
    AVG | COUNT | LATEST | SUM | MAX | MIN | ;

// 0 parameter function
function0:
    ABS | CEIL | FLOOR;
// 1 parameter function
function1:
    ROUND;

topN: TOP_N;

viewAsSeq:
    VIEW_AS_SEQ;

relabels: RELABELS;

parameter:      INTEGER;

order: ASC | DES;

aggregateLabels:
    AGGREGATE_LABELS;

aggregateLabelsFunc:
    AVG | SUM | MAX | MIN;
