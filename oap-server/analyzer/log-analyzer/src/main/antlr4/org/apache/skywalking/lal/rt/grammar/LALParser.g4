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

// Log Analysis Language parser
//
// Covers LAL DSL patterns:
//   filter { parser {} extractor {} sink {} }
//   if (tag("LOG_KIND") == "NGINX_ACCESS_LOG") { ... }
//   text { regexp $/pattern/$ }
//   json { abortOnFailure true }
//   extractor { service parsed.service as String; tag 'key': value }
//   metrics { name "metric_name"; value 1; labels key: val }
//   sink { sampler { rateLimit("id") { rpm 6000 } } }
parser grammar LALParser;

@Header {package org.apache.skywalking.lal.rt.grammar;}

options { tokenVocab=LALLexer; }

// ==================== Top-level ====================

root
    : filterBlock EOF
    ;

// ==================== Filter block ====================

filterBlock
    : FILTER L_BRACE filterContent R_BRACE
    ;

filterContent
    : filterStatement*
    ;

filterStatement
    : parserBlock
    | extractorBlock
    | sinkBlock
    | ifStatement
    | abortBlock
    ;

// ==================== Parser blocks ====================

parserBlock
    : textBlock
    | jsonBlock
    | yamlBlock
    ;

textBlock
    : TEXT L_BRACE textContent R_BRACE
    ;

textContent
    : (regexpStatement | abortOnFailureStatement)*
    ;

regexpStatement
    : REGEXP regexpPattern
    ;

regexpPattern
    : SLASHY_STRING
    | STRING
    ;

jsonBlock
    : JSON L_BRACE jsonContent R_BRACE
    ;

jsonContent
    : abortOnFailureStatement?
    ;

yamlBlock
    : YAML L_BRACE yamlContent R_BRACE
    ;

yamlContent
    : abortOnFailureStatement?
    ;

abortOnFailureStatement
    : ABORT_ON_FAILURE boolValue
    ;

abortBlock
    : ABORT L_BRACE R_BRACE
    ;

// ==================== Extractor block ====================

extractorBlock
    : EXTRACTOR L_BRACE extractorContent R_BRACE
    ;

extractorContent
    : extractorStatement*
    ;

extractorStatement
    : serviceStatement
    | instanceStatement
    | endpointStatement
    | layerStatement
    | traceIdStatement
    | segmentIdStatement
    | spanIdStatement
    | timestampStatement
    | tagStatement
    | metricsBlock
    | ifStatement
    | outputFieldStatement
    ;

serviceStatement
    : SERVICE valueAccess typeCast?
    ;

instanceStatement
    : INSTANCE valueAccess typeCast?
    ;

endpointStatement
    : ENDPOINT valueAccess typeCast?
    ;

layerStatement
    : LAYER valueAccess typeCast?
    ;

traceIdStatement
    : TRACE_ID valueAccess typeCast?
    ;

segmentIdStatement
    : SEGMENT_ID valueAccess typeCast?
    ;

spanIdStatement
    : SPAN_ID valueAccess typeCast?
    ;

timestampStatement
    : TIMESTAMP valueAccess typeCast? (COMMA STRING)?
    ;

outputFieldStatement
    : anyIdentifier valueAccess typeCast?
    ;

tagStatement
    : TAG tagMap
    | TAG STRING COLON valueAccess typeCast?
    ;

tagMap
    : anyIdentifier COLON valueAccess typeCast? (COMMA anyIdentifier COLON valueAccess typeCast?)*
    ;

// ==================== Metrics block ====================

metricsBlock
    : METRICS L_BRACE metricsContent R_BRACE
    ;

metricsContent
    : metricsStatement*
    ;

metricsStatement
    : metricsNameStatement
    | metricsTimestampStatement
    | metricsLabelsStatement
    | metricsValueStatement
    ;

metricsNameStatement
    : NAME valueAccess typeCast?
    ;

metricsTimestampStatement
    : TIMESTAMP valueAccess typeCast?
    ;

metricsLabelsStatement
    : LABELS labelMap
    ;

labelMap
    : labelEntry (COMMA labelEntry)*
    ;

labelEntry
    : anyIdentifier COLON valueAccess typeCast?
    ;

metricsValueStatement
    : VALUE valueAccess typeCast?
    ;

// ==================== Sink block ====================

sinkBlock
    : SINK L_BRACE sinkContent R_BRACE
    ;

sinkContent
    : sinkStatement*
    ;

sinkStatement
    : samplerBlock
    | enforcerStatement
    | dropperStatement
    | ifStatement
    ;

samplerBlock
    : SAMPLER L_BRACE samplerContent R_BRACE
    ;

samplerContent
    : (rateLimitBlock | ifStatement)*
    ;

rateLimitBlock
    : RATE_LIMIT L_PAREN rateLimitId R_PAREN L_BRACE rateLimitContent R_BRACE
    ;

rateLimitId
    : STRING
    ;

rateLimitContent
    : RPM NUMBER
    ;

enforcerStatement
    : ENFORCER L_BRACE R_BRACE
    ;

dropperStatement
    : DROPPER L_BRACE R_BRACE
    ;

// ==================== Control flow ====================

ifStatement
    : IF L_PAREN condition R_PAREN L_BRACE
        ifBody
      R_BRACE
      (ELSE IF L_PAREN condition R_PAREN L_BRACE
        ifBody
      R_BRACE)*
      (ELSE L_BRACE
        ifBody
      R_BRACE)?
    ;

ifBody
    : filterStatement*
    | extractorStatement*
    | sinkStatement*
    | samplerContent
    ;

// ==================== Conditions ====================

condition
    : condition AND condition                     # condAnd
    | condition OR condition                      # condOr
    | NOT condition                               # condNot
    | conditionExpr DEQ conditionExpr             # condEq
    | conditionExpr NEQ conditionExpr             # condNeq
    | conditionExpr GT conditionExpr              # condGt
    | conditionExpr LT conditionExpr              # condLt
    | conditionExpr GTE conditionExpr             # condGte
    | conditionExpr LTE conditionExpr             # condLte
    | conditionExpr                               # condSingle
    ;

conditionExpr
    : valueAccess typeCast?                       # condValueAccess
    | L_PAREN condition R_PAREN                   # condParenGroup
    | STRING                                      # condString
    | NUMBER                                      # condNumber
    | boolValue                                   # condBool
    | NULL                                        # condNull
    | functionInvocation                          # condFunctionCall
    ;

// ==================== Value access ====================

// Accessing parsed values, log fields, and method calls:
//   parsed.level, parsed?.response?.responseCode?.value
//   log.service, log.timestamp, log.serviceInstance
//   tag("LOG_KIND")
//   ProcessRegistry.generateVirtualLocalProcess(...)

valueAccess
    : valueAccessTerm (PLUS valueAccessTerm)*
    ;

valueAccessTerm
    : valueAccessPrimary (valueAccessSegment)*
    ;

valueAccessPrimary
    : PARSED                                      # valueParsed
    | LOG                                         # valueLog
    | PROCESS_REGISTRY                            # valueProcessRegistry
    | IDENTIFIER                                  # valueIdentifier
    | STRING                                      # valueString
    | NUMBER                                      # valueNumber
    | boolValue                                   # valueBool
    | NULL                                        # valueNull
    | functionInvocation                          # valueFunctionCall
    | L_PAREN valueAccess typeCast? R_PAREN       # valueParen
    ;

valueAccessSegment
    : DOT anyIdentifier                           # segmentField
    | QUESTION DOT anyIdentifier                  # segmentSafeField
    | DOT functionInvocation                      # segmentMethod
    | QUESTION DOT functionInvocation             # segmentSafeMethod
    | L_BRACKET NUMBER R_BRACKET                  # segmentIndex
    ;

functionInvocation
    : functionName L_PAREN functionArgList? R_PAREN
    ;

functionName
    : IDENTIFIER
    | TAG
    ;

functionArgList
    : functionArg (COMMA functionArg)*
    ;

functionArg
    : valueAccess typeCast?
    | STRING
    | NUMBER
    | boolValue
    | NULL
    ;

// ==================== Type cast ====================

typeCast
    : AS (STRING_TYPE | LONG_TYPE | INTEGER_TYPE | BOOLEAN_TYPE)
    ;

// ==================== Common ====================

// Allows keywords to be used as identifiers in contexts like field names,
// labels, and value access segments (e.g. parsed.service, parsed.layer).
anyIdentifier
    : IDENTIFIER
    | SERVICE | INSTANCE | ENDPOINT | LAYER
    | TRACE_ID | SEGMENT_ID | SPAN_ID | TIMESTAMP
    | TAG | METRICS
    | REGEXP | ABORT_ON_FAILURE
    | NAME | VALUE | LABELS
    | SAMPLER | RATE_LIMIT | RPM | ENFORCER | DROPPER
    | TEXT | JSON | YAML | FILTER | EXTRACTOR | SINK | ABORT
    ;

boolValue
    : TRUE | FALSE
    ;
