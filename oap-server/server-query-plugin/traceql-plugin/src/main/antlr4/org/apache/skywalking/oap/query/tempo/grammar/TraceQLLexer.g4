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

lexer grammar TraceQLLexer;

// Keywords
AND:         '&&' | 'and';
OR:          '||' | 'or';
NOT:         '!' | 'not';
TRUE:        'true';
FALSE:       'false';
NIL:         'nil';

// Scope selectors
RESOURCE:    'resource';
SPAN:        'span';
// Not support yet
//INTRINSIC:   'intrinsic';
//EVENT:       'event';
//LINK:        'link';

// Operators
DOT:         '.';
COMMA:       ',';
L_PAREN:     '(';
R_PAREN:     ')';
L_BRACE:     '{';
R_BRACE:     '}';
L_BRACKET:   '[';
R_BRACKET:   ']';

// Comparison operators
EQ:          '=';
NEQ:         '!=';
LT:          '<';
LTE:         '<=';
GT:          '>';
GTE:         '>=';
// Not support yet
//RE:          '=~';  // Regex match
//NRE:         '!~';  // Regex not match

// Arithmetic operators
PLUS:        '+';
MINUS:       '-';
STAR:        '*';
DIV:         '/';
MOD:         '%';
POW:         '^';

// Other operators
TILDE:       '~';

// Literals
fragment DIGIT:       [0-9];
fragment LETTER:      [a-zA-Z_];
fragment HEX_DIGIT:   [0-9a-fA-F];

// Identifiers (including intrinsic field names)
IDENTIFIER: LETTER (LETTER | DIGIT | '-')*;


// String literals (double or single quoted)
STRING_LITERAL: '"' (~["\\\r\n] | '\\' .)* '"'
              | '\'' (~['\\\r\n] | '\\' .)* '\'';

// Numeric literals
NUMBER: DIGIT+ ('.' DIGIT+)? ([eE][+-]? DIGIT+)?;

// Duration literals (e.g., 100ms, 1s, 1m, 1h)
DURATION_LITERAL: NUMBER ('us' | 'µs' | 'ms' | 's' | 'm' | 'h');

// Whitespace
WS: [ \t\r\n]+ -> skip;

// Comments
LINE_COMMENT: '//' ~[\r\n]* -> skip;
BLOCK_COMMENT: '/*' .*? '*/' -> skip;
