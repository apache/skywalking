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

// Hierarchy Rule matching expression parser
//
// Parses expressions from hierarchy-definition.yml auto-matching-rules:
//   name:                    "{ (u, l) -> u.name == l.name }"
//   short-name:              "{ (u, l) -> u.shortName == l.shortName }"
//   lower-short-name-remove-ns:
//     "{ (u, l) -> { if(l.shortName.lastIndexOf('.') > 0) return u.shortName == l.shortName.substring(0, l.shortName.lastIndexOf('.')); return false; } }"
//   lower-short-name-with-fqdn:
//     "{ (u, l) -> { if(u.shortName.lastIndexOf(':') > 0) return u.shortName.substring(0, u.shortName.lastIndexOf(':')) == l.shortName.concat('.svc.cluster.local'); return false; } }"
parser grammar HierarchyRuleParser;

@Header {package org.apache.skywalking.hierarchy.rt.grammar;}

options { tokenVocab=HierarchyRuleLexer; }

// ==================== Top-level ====================

// { (u, l) -> body }
matchingRule
    : L_BRACE L_PAREN param COMMA param R_PAREN ARROW ruleBody R_BRACE EOF
    ;

param
    : IDENTIFIER
    ;

ruleBody
    : simpleExpression                      // u.name == l.name
    | blockBody                             // { if(...) ...; return false; }
    ;

// ==================== Block body ====================

blockBody
    : L_BRACE statement+ R_BRACE
    ;

statement
    : ifStatement
    | returnStatement
    ;

// condition(i) guards ifBranch(i); an ifBranch past the last condition is the else.
ifStatement
    : IF L_PAREN condition R_PAREN ifBranch
      (ELSE IF L_PAREN condition R_PAREN ifBranch)*
      (ELSE ifBranch)?
    ;

ifBranch
    : returnStatement
    | blockBody
    ;

returnStatement
    : RETURN returnValue SEMI?
    ;

returnValue
    : ruleExpr DEQ ruleExpr                  # returnComparison
    | ruleExpr NEQ ruleExpr                  # returnNeqComparison
    | ruleExpr                               # returnExpr
    ;

// ==================== Conditions ====================

// Earlier alternatives bind tighter: `!a && b || c` is `((!a) && b) || c`, as in Groovy.
condition
    : NOT condition                           # condNot
    | condition AND condition                 # condAnd
    | condition OR condition                  # condOr
    | L_PAREN condition R_PAREN              # condParen
    | ruleExpr DEQ ruleExpr                  # condEq
    | ruleExpr NEQ ruleExpr                  # condNeq
    | ruleExpr GT ruleExpr                   # condGt
    | ruleExpr LT ruleExpr                   # condLt
    | ruleExpr GTE ruleExpr                  # condGte
    | ruleExpr LTE ruleExpr                  # condLte
    | ruleExpr                               # condExpr
    ;

// ==================== Expressions ====================

simpleExpression
    : ruleExpr DEQ ruleExpr
    | ruleExpr NEQ ruleExpr
    ;

// One alternative for + and -, so they share a precedence level and associate left.
ruleExpr
    : ruleExpr op=(PLUS | MINUS) ruleExpr    # exprAddSub
    | ruleExprPrimary                        # exprPrimary
    ;

ruleExprPrimary
    : methodChain                            # exprMethodChain
    | STRING                                 # exprString
    | NUMBER                                 # exprNumber
    | TRUE                                   # exprTrue
    | FALSE                                  # exprFalse
    ;

// ==================== Method chains ====================

// u.name, l.shortName, l.shortName.lastIndexOf('.'),
// u.shortName.substring(0, l.shortName.lastIndexOf(':'))
// l.shortName.concat('.svc.cluster.local')
methodChain
    : IDENTIFIER (DOT chainSegment)+
    ;

chainSegment
    : IDENTIFIER L_PAREN argList? R_PAREN    # chainMethodCall
    | IDENTIFIER                              # chainFieldAccess
    ;

argList
    : ruleExpr (COMMA ruleExpr)*
    ;
