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

package org.apache.skywalking.oap.query.traceql.parser;

import org.apache.skywalking.oap.query.tempo.grammar.TraceQLParser;
import org.apache.skywalking.oap.query.tempo.grammar.TraceQLParserBaseVisitor;

/**
 * TraceQL query visitor to extract query parameters.
 */
public class TraceQLQueryVisitor extends TraceQLParserBaseVisitor<TraceQLQueryParams> {

    private TraceQLQueryParams params = new TraceQLQueryParams();

    @Override
    public TraceQLQueryParams visitQuery(TraceQLParser.QueryContext ctx) {
        visitChildren(ctx);
        return params;
    }

    @Override
    public TraceQLQueryParams visitAttributeFilterExpr(TraceQLParser.AttributeFilterExprContext ctx) {
        String attribute = extractAttributeName(ctx.attribute());
        String operator = ctx.operator().getText();
        String value = extractStaticValue(ctx.static_());

        // Handle specific attributes
        // Note: unscoped .service.name becomes "service.name", scoped becomes "resource.service.name"
        if ("service.name".equals(attribute) || "resource.service.name".equals(attribute)) {
            if ("=".equals(operator)) {
                params.setServiceName(value);
            }
        } else if ("span.name".equals(attribute) || "name".equals(attribute)) {
            if ("=".equals(operator)) {
                params.setSpanName(value);
            }
        } else if ("http.status_code".equals(attribute) || "span.http.status_code".equals(attribute)) {
            if ("=".equals(operator)) {
                params.setHttpStatusCode(value);
            }
        } else {
            // Store other attributes
            // Remove scope prefix if present (e.g., span.http.method -> http.method)
            String tagKey = removeScopePrefix(attribute);
            params.getTags().put(tagKey, value);
        }

        return visitChildren(ctx);
    }

    @Override
    public TraceQLQueryParams visitIntrinsicFilterExpr(TraceQLParser.IntrinsicFilterExprContext ctx) {
        String field = ctx.intrinsicField().getText();
        String operator = ctx.operator().getText();
        String value = extractStaticValue(ctx.static_());

        // Handle intrinsic fields
        if ("duration".equals(field)) {
            long durationMicros = parseDuration(value);
            if (">".equals(operator) || ">=".equals(operator)) {
                params.setMinDuration(durationMicros);
            } else if ("<".equals(operator) || "<=".equals(operator)) {
                params.setMaxDuration(durationMicros);
            }
        } else if ("name".equals(field)) {
            // name is the span name
            if ("=".equals(operator)) {
                params.setSpanName(value);
            }
        } else if ("status".equals(field)) {
            params.setStatus(value);
        } else if ("kind".equals(field)) {
            params.setKind(value);
        }

        return visitChildren(ctx);
    }

    /**
     * Extract attribute name from attribute context.
     */
    private String extractAttributeName(TraceQLParser.AttributeContext ctx) {
        if (ctx instanceof TraceQLParser.UnscopedAttributeContext) {
            TraceQLParser.UnscopedAttributeContext unscopedCtx = (TraceQLParser.UnscopedAttributeContext) ctx;
            // Extract the dotted identifier (e.g., service.name, http.status_code)
            return extractDottedIdentifier(unscopedCtx.dottedIdentifier());
        } else if (ctx instanceof TraceQLParser.ScopedAttributeContext) {
            TraceQLParser.ScopedAttributeContext scopedCtx = (TraceQLParser.ScopedAttributeContext) ctx;
            String scope = scopedCtx.scope().getText();
            String identifier = extractDottedIdentifier(scopedCtx.dottedIdentifier());
            return scope + "." + identifier;
        }
        return "";
    }

    /**
     * Extract dotted identifier string (e.g., service.name -> "service.name").
     */
    private String extractDottedIdentifier(TraceQLParser.DottedIdentifierContext ctx) {
        if (ctx == null) {
            return "";
        }
        // Join all IDENTIFIER tokens with dots
        return ctx.IDENTIFIER().stream()
            .map(node -> node.getText())
            .reduce((a, b) -> a + "." + b)
            .orElse("");
    }

    /**
     * Extract static value from static context.
     */
    private String extractStaticValue(TraceQLParser.StaticContext ctx) {
        if (ctx instanceof TraceQLParser.StringLiteralContext) {
            String text = ctx.getText();
            // Remove quotes
            return text.substring(1, text.length() - 1);
        } else if (ctx instanceof TraceQLParser.NumericLiteralContext) {
            return ctx.getText();
        } else if (ctx instanceof TraceQLParser.DurationLiteralContext) {
            return ctx.getText();
        } else if (ctx instanceof TraceQLParser.TrueLiteralContext) {
            return "true";
        } else if (ctx instanceof TraceQLParser.FalseLiteralContext) {
            return "false";
        }
        return ctx.getText();
    }

    /**
     * Parse duration string to microseconds.
     *
     * @param duration Duration string (e.g., "100ms", "1s", "1m")
     * @return Duration in microseconds
     */
    private long parseDuration(String duration) {
        if (duration == null || duration.isEmpty()) {
            return 0;
        }

        // Extract numeric value and unit
        String numPart = duration.replaceAll("[^0-9.]", "");
        String unitPart = duration.replaceAll("[0-9.]", "");

        try {
            double value = Double.parseDouble(numPart);

            switch (unitPart) {
                case "ns":
                    return (long) (value / 1000); // Convert to microseconds
                case "us":
                case "µs":
                    return (long) value;
                case "ms":
                    return (long) (value * 1000);
                case "s":
                    return (long) (value * 1_000_000);
                case "m":
                    return (long) (value * 60_000_000);
                case "h":
                    return (long) (value * 3600_000_000L);
                default:
                    // Assume microseconds if no unit
                    return (long) value;
            }
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Remove scope prefix from attribute name.
     * Examples:
     *   span.http.method -> http.method
     *   resource.service.name -> service.name
     *   http.method -> http.method (unchanged)
     *
     * @param attribute Attribute name with or without scope prefix
     * @return Attribute name without scope prefix
     */
    private String removeScopePrefix(String attribute) {
        if (attribute == null) {
            return null;
        }

        // Known scopes: span, resource, event, link, intrinsic
        String[] knownScopes = {"span.", "resource.", "event.", "link.", "intrinsic."};

        for (String scope : knownScopes) {
            if (attribute.startsWith(scope)) {
                return attribute.substring(scope.length());
            }
        }

        return attribute;
    }
}
