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

// Meter Analysis Language parser
//
// Covers MAL expression patterns:
//   metric_name.tagEqual("k","v").sum(["tag"]).rate("PT1M").service(["svc"], Layer.GENERAL)
//   metric1 + metric2, (metric * 100), metric1.div(metric2)
//   tag({tags -> tags.key = "val"}), forEach(["prefix"], {prefix, tags -> ...})
//   .valueEqual(1), .retagByK8sMeta("svc", K8sRetagType.Pod2Service, "pod", "ns")
//   .histogram().histogram_percentile([50,75,90,95,99]).downsampling(SUM)
parser grammar MALParser;

@Header {package org.apache.skywalking.mal.rt.grammar;}

options { tokenVocab=MALLexer; }

// ==================== Top-level ====================

// A MAL expression: arithmetic tree of postfix-chained metric references
expression
    : additiveExpression EOF
    ;

// ==================== Arithmetic ====================

additiveExpression
    : multiplicativeExpression ((PLUS | MINUS) multiplicativeExpression)*
    ;

multiplicativeExpression
    : unaryExpression ((STAR | SLASH) unaryExpression)*
    ;

unaryExpression
    : MINUS unaryExpression                  # unaryNeg
    | postfixExpression                      # unaryPostfix
    | NUMBER                                 # unaryNumber
    ;

// ==================== Postfix (method chaining) ====================

// primary.method1().method2()...
postfixExpression
    : primary (DOT methodCall)*
    ;

primary
    : IDENTIFIER                             // metric name
    | functionCall                           // top-level function: count(metric), topN(...)
    | L_PAREN additiveExpression R_PAREN     // parenthesized: (metric * 100).sum()
    ;

functionCall
    : IDENTIFIER L_PAREN argumentList? R_PAREN
    ;

methodCall
    : IDENTIFIER L_PAREN argumentList? R_PAREN
    ;

// ==================== Arguments ====================

argumentList
    : argument (COMMA argument)*
    ;

argument
    : additiveExpression                     // nested expression (metric ref, number, arithmetic)
    | stringList                             // ["tag1", "tag2"]
    | numberList                             // [50, 75, 90, 95, 99]
    | closureExpression                      // {tags -> ...}
    | enumRef                                // Layer.GENERAL, K8sRetagType.Pod2Service
    | STRING                                 // "PT1M", "k8s-key"
    | boolLiteral                            // true, false
    ;

stringList
    : L_BRACKET STRING (COMMA STRING)* R_BRACKET
    ;

numberList
    : L_BRACKET NUMBER (COMMA NUMBER)* R_BRACKET
    ;

enumRef
    : IDENTIFIER DOT IDENTIFIER
    ;

boolLiteral
    : TRUE | FALSE
    ;

// ==================== Closure expressions ====================
//
// Used in tag(), forEach(), and filter expressions:
//   { tags -> tags.key = "val" }
//   { prefix, tags -> if (tags[prefix + "_process_id"] != null) { ... } }
//   { tags -> tags.job_name == 'mysql-monitoring' }
//   { tags -> { tags.cloud_provider == 'aws' && tags.Namespace == 'AWS/S3' } }

closureExpression
    : L_BRACE closureParams? ARROW closureBody R_BRACE
    ;

closureParams
    : IDENTIFIER (COMMA IDENTIFIER)*
    ;

closureBody
    : closureStatement+
    | L_BRACE closureStatement+ R_BRACE      // optional extra braces: { tags -> { ... } }
    ;

closureStatement
    : ifStatement
    | returnStatement
    | assignmentStatement
    | expressionStatement
    ;

// ==================== Closure statements ====================

ifStatement
    : IF L_PAREN closureCondition R_PAREN closureBlock
      (ELSE ifStatement)?
      (ELSE closureBlock)?
    ;

closureBlock
    : L_BRACE closureStatement* R_BRACE
    ;

returnStatement
    : RETURN closureExpr? SEMI?
    ;

assignmentStatement
    : closureFieldAccess ASSIGN closureExpr SEMI?
    ;

expressionStatement
    : closureExpr SEMI?
    ;

// ==================== Closure expressions (within closures) ====================

closureCondition
    : closureConditionOr
    ;

closureConditionOr
    : closureConditionAnd (OR closureConditionAnd)*
    ;

closureConditionAnd
    : closureConditionPrimary (AND closureConditionPrimary)*
    ;

closureConditionPrimary
    : NOT closureConditionPrimary                                 # conditionNot
    | closureExpr DEQ closureExpr                                 # conditionEq
    | closureExpr NEQ closureExpr                                 # conditionNeq
    | closureExpr GT closureExpr                                  # conditionGt
    | closureExpr LT closureExpr                                  # conditionLt
    | closureExpr GTE closureExpr                                 # conditionGte
    | closureExpr LTE closureExpr                                 # conditionLte
    | closureExpr IN closureListLiteral                            # conditionIn
    | L_PAREN closureCondition R_PAREN                             # conditionParen
    | closureExpr                                                  # conditionExpr
    ;

closureExpr
    : closureExpr PLUS closureExpr                                 # closureAdd
    | closureExpr MINUS closureExpr                                # closureSub
    | closureExpr STAR closureExpr                                 # closureMul
    | closureExpr SLASH closureExpr                                # closureDiv
    | closureExprPrimary                                           # closurePrimary
    ;

closureExprPrimary
    : STRING                                                       # closureString
    | NUMBER                                                       # closureNumber
    | NULL                                                         # closureNull
    | boolLiteral                                                  # closureBool
    | closureMethodChain                                           # closureChain
    ;

closureMethodChain
    : closureTarget (DOT closureChainSegment)* (safeNav closureChainSegment)*
    ;

closureTarget
    : IDENTIFIER
    ;

closureChainSegment
    : IDENTIFIER L_PAREN closureArgList? R_PAREN                   # chainMethodCall
    | IDENTIFIER                                                   # chainFieldAccess
    | L_BRACKET closureExpr R_BRACKET                              # chainIndexAccess
    ;

safeNav
    : QUESTION DOT
    ;

closureArgList
    : closureExpr (COMMA closureExpr)*
    ;

closureFieldAccess
    : IDENTIFIER (DOT IDENTIFIER)* (L_BRACKET closureExpr R_BRACKET)?
    ;

closureListLiteral
    : L_BRACKET (STRING (COMMA STRING)*)? R_BRACKET
    ;
