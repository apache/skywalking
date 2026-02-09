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

package org.apache.skywalking.oal.v2.parser;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;

/**
 * Error listener for OAL parsing that collects detailed error information.
 *
 * This listener captures syntax errors during parsing and provides:
 * - Error location (line and column)
 * - Offending token
 * - Error message
 * - File name for context
 *
 * Example usage:
 * <pre>
 * OALErrorListener errorListener = new OALErrorListener("core.oal");
 * parser.removeErrorListeners();
 * parser.addErrorListener(errorListener);
 *
 * // After parsing
 * if (errorListener.hasErrors()) {
 *     for (OALSyntaxError error : errorListener.getErrors()) {
 *         System.err.println(error.toString());
 *     }
 * }
 * </pre>
 */
public class OALErrorListener extends BaseErrorListener {

    private final String fileName;

    @Getter
    private final List<OALSyntaxError> errors = new ArrayList<>();

    public OALErrorListener(String fileName) {
        this.fileName = fileName;
    }

    @Override
    public void syntaxError(
        Recognizer<?, ?> recognizer,
        Object offendingSymbol,
        int line,
        int charPositionInLine,
        String msg,
        RecognitionException e) {

        String tokenText = null;
        if (offendingSymbol instanceof Token) {
            Token token = (Token) offendingSymbol;
            tokenText = token.getText();
        }

        OALSyntaxError error = new OALSyntaxError(
            fileName,
            line,
            charPositionInLine,
            msg,
            tokenText
        );

        errors.add(error);
    }

    /**
     * Check if any errors were encountered during parsing.
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * Get error count.
     */
    public int getErrorCount() {
        return errors.size();
    }

    /**
     * Get formatted error message including all errors.
     */
    public String getFormattedErrors() {
        if (!hasErrors()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("OAL parsing failed with ").append(errors.size()).append(" error(s):\n");

        for (int i = 0; i < errors.size(); i++) {
            OALSyntaxError error = errors.get(i);
            sb.append("  ").append(i + 1).append(". ").append(error.toString()).append("\n");
        }

        return sb.toString();
    }

    /**
     * Represents a single syntax error in OAL script.
     */
    @Getter
    public static class OALSyntaxError {
        private final String fileName;
        private final int line;
        private final int column;
        private final String message;
        private final String offendingToken;

        public OALSyntaxError(String fileName, int line, int column, String message, String offendingToken) {
            this.fileName = fileName;
            this.line = line;
            this.column = column;
            this.message = message;
            this.offendingToken = offendingToken;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(fileName).append(":").append(line).append(":").append(column);
            sb.append(" - ").append(message);

            if (offendingToken != null && !offendingToken.isEmpty()) {
                sb.append(" (at '").append(offendingToken).append("')");
            }

            return sb.toString();
        }

        /**
         * Get short location string for error reporting.
         */
        public String getLocation() {
            return fileName + ":" + line + ":" + column;
        }
    }
}
