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

// Hierarchy Rule matching expression lexer
//
// Covers expressions like:
//   { (u, l) -> u.name == l.name }
//   { (u, l) -> { if(l.shortName.lastIndexOf('.') > 0) return u.shortName == l.shortName.substring(0, l.shortName.lastIndexOf('.')); return false; } }
lexer grammar HierarchyRuleLexer;

@Header {package org.apache.skywalking.hierarchy.rt.grammar;}

// Keywords
IF:         'if';
ELSE:       'else';
RETURN:     'return';
TRUE:       'true';
FALSE:      'false';

// Comparison and logical operators
DEQ:        '==';
NEQ:        '!=';
AND:        '&&';
OR:         '||';
NOT:        '!';
GT:         '>';
LT:         '<';
GTE:        '>=';
LTE:        '<=';

// Delimiters
DOT:        '.';
COMMA:      ',';
SEMI:       ';';
L_PAREN:    '(';
R_PAREN:    ')';
L_BRACE:    '{';
R_BRACE:    '}';
ARROW:      '->';

// Arithmetic (for substring index arguments)
PLUS:       '+';
MINUS:      '-';

// Literals
NUMBER
    : Digit+
    ;

STRING
    : '\'' (~['\\\r\n] | EscapeSequence)* '\''
    | '"' (~["\\\r\n] | EscapeSequence)* '"'
    ;

// Comments
LINE_COMMENT
    : '//' ~[\r\n]* -> channel(HIDDEN)
    ;

BLOCK_COMMENT
    : '/*' .*? '*/' -> channel(HIDDEN)
    ;

// Whitespace
WS
    : [ \t\r\n]+ -> channel(HIDDEN)
    ;

// Identifiers
IDENTIFIER
    : Letter LetterOrDigit*
    ;

// Fragments
fragment EscapeSequence
    : '\\' [btnfr"'\\]
    ;

fragment Digit
    : [0-9]
    ;

fragment Letter
    : [a-zA-Z_]
    ;

fragment LetterOrDigit
    : Letter
    | [0-9]
    ;
