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

root: expression EOF;

expression
    : expressionNode                   # exprNode
    | L_PAREN expression R_PAREN       # parensOp
    | expression mulDivMod expression  # mulDivModOp
    | expression addSub expression     # addSubOp
    | expression compare expression    # compareOp
    | aggregation L_PAREN expression R_PAREN # aggregationOp
    | mathematical_operator0 L_PAREN expression R_PAREN #mathematicalOperator0OP
    | mathematical_operator1 L_PAREN expression COMMA parameter R_PAREN #mathematicalOperator1OP
    | trend L_PAREN metric COMMA INTEGER R_PAREN #trendOP
    | logical_operator L_PAREN expressionList R_PAREN #logicalOperatorOP
    | topN L_PAREN metric COMMA INTEGER COMMA order R_PAREN  #topNOP
    | relabels L_PAREN expression COMMA label COMMA replaceLabel R_PAREN #relablesOP
    | aggregateLabels L_PAREN expression COMMA aggregateLabelsFunc R_PAREN #aggregateLabelsOp
    | sort_values L_PAREN expression (COMMA INTEGER)? COMMA order R_PAREN #sortValuesOP
    | sort_label_values L_PAREN expression COMMA order COMMA labelNameList R_PAREN #sortLabelValuesOP
    ;

expressionList
    : expression (COMMA expression)*;

expressionNode:  metric| scalar;

addSub:          ADD | SUB ;
mulDivMod:       MUL | DIV | MOD;
compare:        (DEQ | NEQ | LTE | LT | GTE | GT) BOOL?;

metricName:      NAME_STRING;
metric:   metricName | metricName L_BRACE labelList? R_BRACE;

labelName:       NAME_STRING | GENERAL_LABEL_NAME;
labelNameList:   labelName (COMMA labelName)*;
labelValue:      VALUE_STRING;
label:           labelName EQ labelValue;
labelList:       label (COMMA label)*;

replaceLabel:    label;

scalar:   INTEGER | DECIMAL;

aggregation:
    AVG | COUNT | LATEST | SUM | MAX | MIN | ;

// 0 parameter function
mathematical_operator0:
    ABS | CEIL | FLOOR;
// 1 parameter function
mathematical_operator1:
    ROUND;

trend:
    INCREASE | RATE;

topN: TOP_N;

logical_operator:
    VIEW_AS_SEQ | IS_PRESENT;

relabels: RELABELS;

parameter:      INTEGER;

order: ASC | DES;

aggregateLabels:
    AGGREGATE_LABELS;

aggregateLabelsFunc: aggregateLabelsFuncName (L_PAREN labelNameList R_PAREN)?;

aggregateLabelsFuncName:
    AVG | SUM | MAX | MIN;

sort_values:
    SORT_VALUES;

sort_label_values:
    SORT_LABEL_VALUES;
