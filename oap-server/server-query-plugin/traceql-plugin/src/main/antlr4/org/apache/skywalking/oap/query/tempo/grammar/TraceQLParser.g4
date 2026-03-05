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

parser grammar TraceQLParser;

options {
    tokenVocab = TraceQLLexer;
}

// Entry point
query
    : spansetExpression EOF
    ;

// Spanset expressions
spansetExpression
    : L_BRACE spansetFilter? R_BRACE                                    # SpansetFilterExpr
    | spansetExpression AND spansetExpression                           # SpansetAndExpr
    | spansetExpression OR spansetExpression                            # SpansetOrExpr
    | L_PAREN spansetExpression R_PAREN                                # SpansetParenExpr
    ;

// Spanset filter (inside braces)
spansetFilter
    : fieldExpression (AND fieldExpression)*
    ;

// Field expressions
fieldExpression
    : attribute operator staticValue                                    # AttributeFilterExpr
    | intrinsicField operator staticValue                               # IntrinsicFilterExpr
    | attribute                                                         # AttributeExistsExpr
    | NOT fieldExpression                                               # NotExpr
    | L_PAREN fieldExpression R_PAREN                                  # ParenExpr
    ;

// Attribute (scoped or unscoped)
// Examples: .service.name, resource.service.name, .http.status_code
attribute
    : DOT dottedIdentifier                                              # UnscopedAttribute
    | scope DOT dottedIdentifier                                        # ScopedAttribute
    ;

// Dotted identifier to support nested attributes like service.name, http.status_code
dottedIdentifier
    : IDENTIFIER (DOT IDENTIFIER)*
    ;

// Scope
scope
    : RESOURCE
    | SPAN
    | INTRINSIC
    | EVENT
    | LINK
    ;

// Intrinsic fields (using IDENTIFIER to avoid keyword conflicts)
intrinsicField
    : IDENTIFIER  // duration, name, status, kind, parent, traceID, rootName, rootServiceName
    ;

// Operators
operator
    : EQ
    | NEQ
    | LT
    | LTE
    | GT
    | GTE
    | RE
    | NRE
    ;

// Static values
staticValue
    : STRING_LITERAL                                                    # StringLiteral
    | NUMBER                                                            # NumericLiteral
    | DURATION_LITERAL                                                  # DurationLiteral
    | TRUE                                                              # TrueLiteral
    | FALSE                                                             # FalseLiteral
    | NIL                                                               # NilLiteral
    ;

