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

lexer grammar MQELexer;

// Keywords
BOOL options { caseInsensitive=true; }: 'bool';
GENERAL_LABEL_NAME:       '_';

// Constructors symbols
DOT:         '.';
COMMA:       ',';
L_PAREN:     '(';
R_PAREN:     ')';
L_BRACKET:   '[';
R_BRACKET:   ']';
L_BRACE:     '{';
R_BRACE:     '}';
EQ:          '=';

// Scalar Binary operators
SUB:         '-';
ADD:         '+';
MUL:         '*';
DIV:         '/';
MOD:         '%';
DEQ:         '==';
NEQ:         '!=';
LTE:         '<=';
LT:          '<';
GTE:         '>=';
GT:          '>';

// Bool operators
AND:         '&&';
OR:          '||';

// Aggregation operators
AVG:         'avg';
COUNT:       'count';
LATEST:      'latest';
MAX:         'max';
MIN:         'min';
SUM:         'sum';

// Functions
ABS:         'abs';
CEIL:        'ceil';
FLOOR:       'floor';
ROUND:       'round';
INCREASE:    'increase';
RATE:        'rate';

// TopN
TOP_N:        'top_n';
TOP_N_OF:     'top_n_of';

// ViewAsSeq
VIEW_AS_SEQ: 'view_as_seq';

// IsPresent
IS_PRESENT: 'is_present';

// Relabels
RELABELS:     'relabels';

// Order
ASC  options { caseInsensitive=true; }: 'asc';
DES options { caseInsensitive=true; }: 'des';

// AGGREGATE_LABELS
AGGREGATE_LABELS:   'aggregate_labels';

// Sort
SORT_VALUES: 'sort_values';
SORT_LABEL_VALUES: 'sort_label_values';

// Attributes
ATTR0: 'attr0';
ATTR1: 'attr1';
ATTR2: 'attr2';
ATTR3: 'attr3';
ATTR4: 'attr4';
ATTR5: 'attr5';

// Literals
INTEGER: Digit+;
DECIMAL: Digit+ DOT Digit+;
NAME_STRING: NameLetter+;
VALUE_STRING: '\'' .*? '\'' | '"' .*? '"';


// Fragments
fragment Digit: [0-9];
fragment NameLetter: [a-zA-Z0-9_];

WS : [ \t\r\n]+ -> skip;
