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

package org.apache.skywalking.oap.query.traceql.rt;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.apache.skywalking.oap.query.tempo.grammar.TraceQLParser;
import org.apache.skywalking.oap.query.tempo.grammar.TraceQLParserBaseVisitor;
import org.apache.skywalking.oap.query.traceql.exception.IllegalExpressionException;
import org.apache.skywalking.oap.server.core.Const;

/**
 * TraceQL query visitor to extract query parameters.
 */
public class TraceQLQueryVisitor extends TraceQLParserBaseVisitor<TraceQLParseResult> {
    private static final List<String> STATUS_VALUES = Arrays.asList("error", "ok", "unset");
    private static final List<String> KIND_VALUES = Arrays.asList(
        "unspecified", "internal", "server", "client", "producer", "consumer");

    private final TraceQLQueryParams params = new TraceQLQueryParams();

    @Override
    public TraceQLParseResult visitQuery(TraceQLParser.QueryContext ctx) {
        visitChildren(ctx);
        return TraceQLParseResult.of(params);
    }

    /**
     * Every construct this visitor does not map is refused with {@link IllegalArgumentException}, which the handlers
     * answer with 400. Dropping it would return traces the query excludes, see #14093.
     */
    @Override
    public TraceQLParseResult visitAttributeFilterExpr(TraceQLParser.AttributeFilterExprContext ctx) {
        String attribute = extractAttributeName(ctx.attribute());
        String operator = ctx.operator().getText();
        String value = extractStaticValue(ctx.staticValue());
        if (!"=".equals(operator)) {
            throw new IllegalArgumentException(
                "Unsupported operator " + operator + " on " + attribute + ": attributes support = only");
        }

        // Handle specific attributes
        // Note: unscoped .service.name becomes "service.name", scoped becomes "resource.service.name"
        switch (attribute) {
            case "service.name":
            case "resource.service.name":
            case "resource.service":
                params.setServiceName(value);
                break;
            case "resource.remote.service":
                params.setRemoteServiceName(value);
                break;
            case "resource.instance":
                params.setServiceInstance(value);
                break;
            case "span.name":
            case "name":
                params.setSpanName(value);
                break;
            case "http.status_code":
            case "span.http.status_code":
                params.setHttpStatusCode(value);
                break;
            default:
                // Store other attributes
                // Remove scope prefix if present (e.g., span.http.method -> http.method)
                String tagKey = removeScopePrefix(attribute);
                params.getTags().put(tagKey, value);
                break;
        }

        return visitChildren(ctx);
    }

    @Override
    public TraceQLParseResult visitIntrinsicFilterExpr(TraceQLParser.IntrinsicFilterExprContext ctx) {
        String field = ctx.intrinsicField().getText();
        String operator = ctx.operator().getText();
        String value = extractStaticValue(ctx.staticValue());

        // Tempo spells the span intrinsics both ways, `kind` and `span:kind`. The other scopes (trace:, event:,
        // link:, instrumentation:) have no column to filter on, so they are rejected rather than ignored.
        final int colon = field.indexOf(':');
        if (colon > 0) {
            final String scope = field.substring(0, colon);
            if (!"span".equals(scope)) {
                throw new IllegalArgumentException(
                    "Unsupported intrinsic " + field + ": only span intrinsics (name, kind, status, duration) can be filtered");
            }
            field = field.substring(colon + 1);
        }

        // Handle intrinsic fields
        if ("duration".equals(field)) {
            if (!(">".equals(operator) || ">=".equals(operator) || "<".equals(operator) || "<=".equals(operator))) {
                throw new IllegalArgumentException(
                    "Unsupported operator " + operator + " on duration: use >, >=, < or <=");
            }
            try {
                long durationMicros = parseDuration(value);
                if (">".equals(operator) || ">=".equals(operator)) {
                    params.setMinDuration(durationMicros);
                } else {
                    params.setMaxDuration(durationMicros);
                }
            } catch (IllegalExpressionException e) {
                throw new IllegalArgumentException(e.getMessage());
            }
            return visitChildren(ctx);
        }
        if (!"=".equals(operator)) {
            throw new IllegalArgumentException(
                "Unsupported operator " + operator + " on " + field + ": intrinsics support = only");
        }
        if ("name".equals(field)) {
            params.setSpanName(value);
        } else if ("status".equals(field)) {
            String status = value.toLowerCase(Locale.ROOT);
            if (!STATUS_VALUES.contains(status)) {
                throw new IllegalArgumentException("Unsupported status value " + value + ": expected one of " + STATUS_VALUES);
            }
            params.setStatus(status);
        } else if ("kind".equals(field)) {
            String kind = value.toLowerCase(Locale.ROOT);
            if (!KIND_VALUES.contains(kind)) {
                throw new IllegalArgumentException("Unsupported kind value " + value + ": expected one of " + KIND_VALUES);
            }
            params.setKind(kind);
        } else {
            throw new IllegalArgumentException(
                "Unsupported intrinsic " + field + ": only name, status, kind and duration can be filtered");
        }

        return visitChildren(ctx);
    }

    @Override
    public TraceQLParseResult visitNotExpr(TraceQLParser.NotExprContext ctx) {
        throw new IllegalArgumentException("Negation (!) is not supported");
    }

    @Override
    public TraceQLParseResult visitAttributeExistsExpr(TraceQLParser.AttributeExistsExprContext ctx) {
        throw new IllegalArgumentException(
            "Attribute existence checks are not supported: compare " + extractAttributeName(ctx.attribute()) + " with =");
    }

    @Override
    public TraceQLParseResult visitSpansetAndExpr(TraceQLParser.SpansetAndExprContext ctx) {
        throw new IllegalArgumentException("Multiple spansets are not supported: put every condition in one {...}");
    }

    @Override
    public TraceQLParseResult visitSpansetOrExpr(TraceQLParser.SpansetOrExprContext ctx) {
        throw new IllegalArgumentException("Multiple spansets are not supported: || between {...} has no equivalent here");
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
            return scope + Const.POINT + identifier;
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
            .reduce((a, b) -> a + Const.POINT + b)
            .orElse("");
    }

    /**
     * Extract static value from static context.
     */
    private String extractStaticValue(TraceQLParser.StaticValueContext ctx) {
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
    public static long parseDuration(String duration) throws IllegalExpressionException {
        if (duration == null || duration.isEmpty()) {
            throw new IllegalExpressionException("Duration string cannot be null or empty");
        }

        // Extract numeric value and unit
        String numPart = duration.replaceAll("[^0-9.]", "");
        String unitPart = duration.replaceAll("[0-9.]", "");

        try {
            double value = Double.parseDouble(numPart);

            switch (unitPart) {
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
            throw new IllegalExpressionException("Duration string cannot be null or empty.");
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
