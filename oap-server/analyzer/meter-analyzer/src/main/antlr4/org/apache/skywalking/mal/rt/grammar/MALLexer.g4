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

// Meter Analysis Language lexer
lexer grammar MALLexer;

@Header {package org.apache.skywalking.mal.rt.grammar;}

// Operators
PLUS:       '+';
MINUS:      '-';
STAR:       '*';
SLASH:      '/';

// Comparison
DEQ:        '==';
NEQ:        '!=';
AND:        '&&';
OR:         '||';

// Delimiters
DOT:        '.';
COMMA:      ',';
L_PAREN:    '(';
R_PAREN:    ')';
L_BRACKET:  '[';
R_BRACKET:  ']';
L_BRACE:    '{';
R_BRACE:    '}';
SEMI:       ';';
COLON:      ':';
DOUBLE_COLON: '::';
QUESTION:   '?';
ARROW:      '->';
ASSIGN:     '=';
GT:         '>';
LT:         '<';
GTE:        '>=';
LTE:        '<=';
NOT:        '!';

// Regex match operator: switches to REGEX_MODE to lex the pattern
REGEX_MATCH: '=~' -> pushMode(REGEX_MODE);

// Keywords
DEF:        'def';
IF:         'if';
ELSE:       'else';
RETURN:     'return';
NULL:       'null';
TRUE:       'true';
FALSE:      'false';
IN:         'in';

// Literals
NUMBER
    : Digit+ ('.' Digit+)?
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

// Identifiers - must come after keywords
IDENTIFIER
    : Letter LetterOrDigit*
    ;

// Fragments
fragment EscapeSequence
    : '\\' [btnfr"'\\]
    | '\\' ([0-3]? [0-7])? [0-7]
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

// ==================== Regex mode ====================
// Activated after '=~', lexes a /pattern/ regex literal, then pops back.
mode REGEX_MODE;

REGEX_WS
    : [ \t\r\n]+ -> channel(HIDDEN)
    ;

REGEX_LITERAL
    : '/' RegexBodyChar+ '/'  -> popMode
    ;

fragment RegexBodyChar
    : '\\' .         // escaped character (e.g. \. \( \[ )
    | ~[/\r\n]       // anything except / and newline
    ;
