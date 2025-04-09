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

lexer grammar PromQLLexer;

// Keywords
BOOL options { caseInsensitive=true; }: 'bool';

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

//regex-match
RM:          '=~';
//regex-not-match
NRM:         '!~';

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

// Aggregation operators
AVG:         'avg';
MAX:         'max';
MIN:         'min';
SUM:         'sum';

BY:          'by';
WITHOUT:     'without';

// Literals
NUMBER: Digit+ (DOT Digit+)?;
DURATION: Digit+ ('ms' | 's' | 'm' | 'h' | 'd' | 'w');
NAME_STRING: NameLetter+;
VALUE_STRING: '\'' .*? '\'' | '"' .*? '"';


// Fragments
fragment Digit: [0-9];
fragment NameLetter: [a-zA-Z0-9_];

WS : [ \t\r\n]+ -> skip;
