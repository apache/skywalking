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
 */


// Trace Analysis Language parser
//
// Covers TAL DSL patterns:
//   filter { extractor {} sink {} persist {} }
//   if (meta.kind == "SERVER") { ... }
//   extractor { layer "OTLP_GENERAL"; def svc = service(meta.service, meta.layer) }
//   metrics { name "service_cpm"; scope svc; function cpm; value 1 }
//   metrics { name "service_percentile"; scope svc; function percentile; value meta.latency
//             buckets [10, 25, 50] }
//   sink { sampler { rateLimit("id") { rpm 6000 } } }
//   persist { enforcer {} }
parser grammar TALParser;

@Header {package org.apache.skywalking.tal.rt.grammar;}

options { tokenVocab=TALLexer; }

// ==================== Top-level ====================

root
    : filterBlock EOF
    ;

// ==================== Filter block ====================

filterBlock
    : FILTER L_BRACE filterContent R_BRACE
    ;

// SEMI is an optional separator everywhere, so a `metrics {}` block fits on one line.
filterContent
    : (filterStatement | SEMI)*
    ;

filterStatement
    : extractorBlock
    | sinkBlock
    | persistBlock
    | ifStatement
    | abortBlock
    | defStatement
    | prepareNetworkAliasStatement
    | buildNetworkAliasStatement
    ;

abortBlock
    : ABORT L_BRACE R_BRACE
    ;

// ==================== Extractor block ====================

extractorBlock
    : EXTRACTOR L_BRACE extractorContent R_BRACE
    ;

extractorContent
    : (extractorStatement | SEMI)*
    ;

extractorStatement
    : defStatement
    | serviceStatement
    | instanceStatement
    | endpointStatement
    | layerStatement
    | tagStatement
    | prepareNetworkAliasStatement
    | buildNetworkAliasStatement
    | metricsBlock
    | ifStatement
    | abortBlock
    | outputFieldStatement
    ;

defStatement
    : DEF IDENTIFIER ASSIGN valueAccess typeCast?
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

// A network alias is built from one call only: the caller's span and the SERVER span whose parentSpanId is that
// span's spanId. `prepareNetworkAlias <peer>` sends the caller's half, the peer it dialed, to the trace's owner, from
// a CLIENT span or from a SERVER span that passed its own context on;
// `buildNetworkAlias <address>` marks a SERVER span as the callee half, and the owner evaluates the address, which
// reads the matched caller half as `pairing.*`, to file the row the callers' resolveNetworkAlias(...) reads.
prepareNetworkAliasStatement
    : PREPARE_NETWORK_ALIAS valueAccess typeCast?
    ;

buildNetworkAliasStatement
    : BUILD_NETWORK_ALIAS valueAccess typeCast?
    ;

// ==================== Metrics block ====================

metricsBlock
    : METRICS L_BRACE metricsContent R_BRACE
    ;

metricsContent
    : (metricsStatement | SEMI)*
    ;

metricsStatement
    : metricsNameStatement
    | metricsScopeStatement
    | metricsFunctionStatement
    | metricsValueStatement
    | metricsMatchStatement
    | metricsStatusStatement
    | metricsLabelsStatement
    | metricsLabelLimitStatement
    | metricsBucketsStatement
    | metricsRanksStatement
    | metricsTimestampStatement
    ;

metricsNameStatement
    : NAME valueAccess typeCast?
    ;

// The entity the value lands on: an entity constructor, or a def variable holding one.
metricsScopeStatement
    : SCOPE valueAccess
    ;

metricsFunctionStatement
    : FUNCTION anyIdentifier
    ;

metricsValueStatement
    : VALUE valueAccess typeCast?
    ;

// `percent` counts a span as a match when this holds.
metricsMatchStatement
    : MATCH condition
    ;

// `apdex` counts a span as successful when this holds.
metricsStatusStatement
    : STATUS condition
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

// The most label sets one row of a labeled metric holds per minute; a new label set beyond it is not counted.
metricsLabelLimitStatement
    : LABEL_LIMIT NUMBER
    ;

metricsBucketsStatement
    : BUCKETS numberList
    ;

metricsRanksStatement
    : RANKS numberList
    ;

metricsTimestampStatement
    : TIMESTAMP valueAccess typeCast?
    ;

numberList
    : L_BRACKET NUMBER (COMMA NUMBER)* R_BRACKET
    ;

// ==================== Sink block ====================

sinkBlock
    : SINK L_BRACE sinkContent R_BRACE
    ;

sinkContent
    : (sinkStatement | SEMI)*
    ;

sinkStatement
    : samplerBlock
    | enforcerStatement
    | dropperStatement
    | ifStatement
    | abortBlock
    ;

// ==================== Persist block ====================

// The span's own fate, voted on with the same statements a sink uses.
persistBlock
    : PERSIST L_BRACE persistContent R_BRACE
    ;

persistContent
    : (persistStatement | SEMI)*
    ;

persistStatement
    : samplerBlock
    | enforcerStatement
    | dropperStatement
    | ifStatement
    | abortBlock
    ;

samplerBlock
    : SAMPLER L_BRACE samplerContent R_BRACE
    ;

samplerContent
    : (rateLimitBlock | ifStatement | SEMI)*
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
    : (filterStatement | SEMI)*
    | (extractorStatement | SEMI)*
    | (sinkStatement | SEMI)*
    | (persistStatement | SEMI)*
    | samplerContent
    ;

// ==================== Conditions ====================

// Alternative order is precedence in an ANTLR left-recursive rule: NOT binds tightest, then AND, then OR, so
// `!peer && hasAttr("x")` is `(!peer) && hasAttr("x")`, as in Java.
condition
    : NOT condition                               # condNot
    | condition AND condition                     # condAnd
    | condition OR condition                      # condOr
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

// Accessing the span, the metadata and the span's attributes:
//   span.name, span?.status?.message, span.eventsCount
//   meta.service, meta.latency, meta.kind, meta.root
//   attr("db.system.name", "db.system"), hasAttr("http.route"), resource("telemetry.sdk.language")
//   service(meta.service, meta.layer), serviceRelation(CLIENT, src, srcLayer, dst, dstLayer, comp)

// Arithmetic / string-concat expressions. Operator precedence: * / bind tighter than + -.
// All four operators share the same flattened model in the AST: each level produces a list of
// operands plus a list of operators (size N-1).
valueAccess
    : valueAccessAdd
    ;

valueAccessAdd
    : valueAccessMul ((PLUS | MINUS) valueAccessMul)*
    ;

valueAccessMul
    : valueAccessTerm ((STAR | SLASH) valueAccessTerm)*
    ;

valueAccessTerm
    : valueAccessPrimary (valueAccessSegment)*
    ;

valueAccessPrimary
    : SPAN                                        # valueSpan
    | META                                        # valueMeta
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

// The entity constructors are ordinary invocations the compiler recognizes by name, so
// `service`, `instance` and `endpoint` have to be usable here as well as as statements.
functionInvocation
    : functionName L_PAREN functionArgList? R_PAREN
    ;

functionName
    : anyIdentifier
    ;

functionArgList
    : functionArg (COMMA functionArg)*
    ;

functionArg
    : detectPoint
    | valueAccess typeCast?
    | STRING
    | NUMBER
    | boolValue
    | NULL
    ;

detectPoint
    : CLIENT
    | SERVER
    ;

// ==================== Type cast ====================

typeCast
    : AS (STRING_TYPE | LONG_TYPE | INTEGER_TYPE | DOUBLE_TYPE | FLOAT_TYPE | BOOLEAN_TYPE | qualifiedName)
    ;

qualifiedName
    : IDENTIFIER (DOT IDENTIFIER)*
    ;

// ==================== Common ====================

// Allows keywords to be used as identifiers in contexts like output field names, label keys
// and chain segments, so `labels service: meta.service` and `meta.status` parse.
anyIdentifier
    : IDENTIFIER
    | SERVICE | INSTANCE | ENDPOINT | LAYER
    | TAG | METRICS | PREPARE_NETWORK_ALIAS | BUILD_NETWORK_ALIAS
    | NAME | SCOPE | FUNCTION | VALUE | MATCH | STATUS
    | LABELS | LABEL_LIMIT | BUCKETS | RANKS | TIMESTAMP
    | SAMPLER | RATE_LIMIT | RPM | ENFORCER | DROPPER
    | FILTER | EXTRACTOR | SINK | PERSIST | ABORT
    | DEF
    ;

boolValue
    : TRUE | FALSE
    ;
