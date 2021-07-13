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


// Observability Analysis Language lexer
lexer grammar OALLexer;

@Header {package org.apache.skywalking.oal.rt.grammar;}

// Keywords

FROM: 'from';
FILTER: 'filter';
DISABLE: 'disable';
SRC_ALL: 'All';
SRC_SERVICE: 'Service';
SRC_SERVICE_INSTANCE: 'ServiceInstance';
SRC_ENDPOINT: 'Endpoint';
SRC_SERVICE_RELATION: 'ServiceRelation';
SRC_SERVICE_INSTANCE_RELATION: 'ServiceInstanceRelation';
SRC_ENDPOINT_RELATION: 'EndpointRelation';
SRC_SERVICE_INSTANCE_JVM_CPU: 'ServiceInstanceJVMCPU';
SRC_SERVICE_INSTANCE_JVM_MEMORY: 'ServiceInstanceJVMMemory';
SRC_SERVICE_INSTANCE_JVM_MEMORY_POOL: 'ServiceInstanceJVMMemoryPool';
SRC_SERVICE_INSTANCE_JVM_GC: 'ServiceInstanceJVMGC';
SRC_SERVICE_INSTANCE_JVM_THREAD: 'ServiceInstanceJVMThread';
SRC_SERVICE_INSTANCE_JVM_CLASS: 'ServiceInstanceJVMClass';
SRC_DATABASE_ACCESS: 'DatabaseAccess';
SRC_SERVICE_INSTANCE_CLR_CPU: 'ServiceInstanceCLRCPU';
SRC_SERVICE_INSTANCE_CLR_GC: 'ServiceInstanceCLRGC';
SRC_SERVICE_INSTANCE_CLR_THREAD: 'ServiceInstanceCLRThread';
SRC_ENVOY_INSTANCE_METRIC: 'EnvoyInstanceMetric';
SRC_EVENT: 'Event';

// Browser keywords
SRC_BROWSER_APP_PERF: 'BrowserAppPerf';
SRC_BROWSER_APP_PAGE_PERF: 'BrowserAppPagePerf';
SRC_BROWSER_APP_SINGLE_VERSION_PERF: 'BrowserAppSingleVersionPerf';
SRC_BROWSER_APP_TRAFFIC: 'BrowserAppTraffic';
SRC_BROWSER_APP_PAGE_TRAFFIC: 'BrowserAppPageTraffic';
SRC_BROWSER_APP_SINGLE_VERSION_TRAFFIC: 'BrowserAppSingleVersionTraffic';

// Constructors symbols

DOT:                                 '.';
LR_BRACKET:                          '(';
RR_BRACKET:                          ')';
LS_BRACKET:                          '[';
RS_BRACKET:                          ']';
COMMA:                               ',';
SEMI:                                ';';
EQUAL:                               '=';
DUALEQUALS:                          '==';
ALL:                                 '*';
GREATER:                             '>';
LESS:                                '<';
GREATER_EQUAL:                       '>=';
LESS_EQUAL:                          '<=';
NOT_EQUAL:                           '!=';
LIKE:                                'like';
IN:                                  'in';
CONTAIN:                            'contain';
NOT_CONTAIN:                        'not contain';

// Literals

BOOL_LITERAL:       'true'
            |       'false'
            ;

NUMBER_LITERAL :   Digits+;

CHAR_LITERAL:       '\'' (~['\\\r\n] | EscapeSequence) '\'';

STRING_LITERAL:     '"' (~["\\\r\n] | EscapeSequence)* '"';

DelimitedComment
    : '/*' ( DelimitedComment | . )*? '*/'
      -> channel(HIDDEN)
    ;

LineComment
    : '//' ~[\u000A\u000D]*
      -> channel(HIDDEN)
    ;

SPACE:                               [ \t\r\n]+    -> channel(HIDDEN);

// Identifiers

IDENTIFIER:         Letter LetterOrDigit*;

// Fragment rules

fragment EscapeSequence
    : '\\' [btnfr"'\\]
    | '\\' ([0-3]? [0-7])? [0-7]
    | '\\' 'u'+ HexDigit HexDigit HexDigit HexDigit
    ;

fragment HexDigits
    : HexDigit ((HexDigit | '_')* HexDigit)?
    ;

fragment HexDigit
    : [0-9a-fA-F]
    ;

fragment Digits
    : [0-9] ([0-9_]* [0-9])?
    ;

fragment LetterOrDigit
    : Letter
    | [0-9]
    ;

fragment Letter
    : [a-zA-Z$_] // these are the "java letters" below 0x7F
    | ~[\u0000-\u007F\uD800-\uDBFF] // covers all characters above 0x7F which are not a surrogate
    | [\uD800-\uDBFF] [\uDC00-\uDFFF] // covers UTF-16 surrogate pairs encodings for U+10000 to U+10FFFF
    ;
