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

// Log Analysis Language lexer
lexer grammar LALLexer;

@Header {package org.apache.skywalking.lal.rt.grammar;}

// Keywords - block structure
FILTER:         'filter';
TEXT:           'text';
JSON:          'json';
YAML:          'yaml';
EXTRACTOR:     'extractor';
SINK:          'sink';
ABORT:         'abort';

// Keywords - extractor statements
SERVICE:       'service';
INSTANCE:      'instance';
ENDPOINT:      'endpoint';
LAYER:         'layer';
TRACE_ID:      'traceId';
SEGMENT_ID:    'segmentId';
SPAN_ID:       'spanId';
TIMESTAMP:     'timestamp';
TAG:           'tag';
METRICS:       'metrics';
REGEXP:        'regexp';
ABORT_ON_FAILURE: 'abortOnFailure';
NAME:          'name';
VALUE:         'value';
LABELS:        'labels';

// Keywords - sink statements
SAMPLER:       'sampler';
RATE_LIMIT:    'rateLimit';
RPM:           'rpm';
ENFORCER:      'enforcer';
DROPPER:       'dropper';

// Keywords - local variables
DEF:           'def';

// Keywords - control flow
IF:            'if';
ELSE:          'else';

// Keywords - type cast
AS:            'as';
STRING_TYPE:   'String';
LONG_TYPE:     'Long';
INTEGER_TYPE:  'Integer';
BOOLEAN_TYPE:  'Boolean';

// Keywords - built-in references
LOG:           'log';
PARSED:        'parsed';

// Keywords - utility class references
PROCESS_REGISTRY: 'ProcessRegistry';

// Comparison and logical operators
DEQ:           '==';
NEQ:           '!=';
AND:           '&&';
OR:            '||';
NOT:           '!';
GT:            '>';
LT:            '<';
GTE:           '>=';
LTE:           '<=';

// Delimiters
DOT:           '.';
COMMA:         ',';
COLON:         ':';
SEMI:          ';';
L_PAREN:       '(';
R_PAREN:       ')';
L_BRACE:       '{';
R_BRACE:       '}';
L_BRACKET:     '[';
R_BRACKET:     ']';
QUESTION:      '?';
ASSIGN:        '=';

// Arithmetic
PLUS:          '+';
MINUS:         '-';
STAR:          '*';
SLASH:         '/';

// Literals
TRUE:          'true';
FALSE:         'false';
NULL:          'null';

NUMBER
    : Digit+ ('.' Digit+)?
    ;

// String literal: single or double quoted
STRING
    : '\'' (~['\\\r\n] | EscapeSequence)* '\''
    | '"' (~["\\\r\n] | EscapeSequence)* '"'
    ;

// Groovy-style slashy string for regex patterns: $/pattern/$
SLASHY_STRING
    : '$/' .*? '/$'
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
    | '\\' ([0-3]? [0-7])? [0-7]
    | '\\' .                                 // catch-all for regex escapes like \d, \w, \s
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
