package com.a.eye.skywalking.collector.worker.segment.entity;

import com.google.gson.stream.JsonToken;
import com.google.gson.stream.MalformedJsonException;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.Reader;

/**
 * Copy from {@link com.google.gson.stream.JsonReader}, modify the {@link #nextString()} method, make it return quoted
 * string and unquoted string.
 *
 * @author pengys5
 */
public class SegmentJsonReader implements Closeable {
    /** The only non-execute prefix this parser permits */
    private static final char[] NON_EXECUTE_PREFIX = ")]}'\n".toCharArray();
    private static final long MIN_INCOMPLETE_INTEGER = Long.MIN_VALUE / 10;

    private static final int PEEKED_NONE = 0;
    private static final int PEEKED_BEGIN_OBJECT = 1;
    private static final int PEEKED_END_OBJECT = 2;
    private static final int PEEKED_BEGIN_ARRAY = 3;
    private static final int PEEKED_END_ARRAY = 4;
    private static final int PEEKED_TRUE = 5;
    private static final int PEEKED_FALSE = 6;
    private static final int PEEKED_NULL = 7;
    private static final int PEEKED_SINGLE_QUOTED = 8;
    private static final int PEEKED_DOUBLE_QUOTED = 9;
    private static final int PEEKED_UNQUOTED = 10;
    /** When this is returned, the string value is stored in peekedString. */
    private static final int PEEKED_BUFFERED = 11;
    private static final int PEEKED_SINGLE_QUOTED_NAME = 12;
    private static final int PEEKED_DOUBLE_QUOTED_NAME = 13;
    private static final int PEEKED_UNQUOTED_NAME = 14;
    /** When this is returned, the integer value is stored in peekedLong. */
    private static final int PEEKED_LONG = 15;
    private static final int PEEKED_NUMBER = 16;
    private static final int PEEKED_EOF = 17;

    /* State machine when parsing numbers */
    private static final int NUMBER_CHAR_NONE = 0;
    private static final int NUMBER_CHAR_SIGN = 1;
    private static final int NUMBER_CHAR_DIGIT = 2;
    private static final int NUMBER_CHAR_DECIMAL = 3;
    private static final int NUMBER_CHAR_FRACTION_DIGIT = 4;
    private static final int NUMBER_CHAR_EXP_E = 5;
    private static final int NUMBER_CHAR_EXP_SIGN = 6;
    private static final int NUMBER_CHAR_EXP_DIGIT = 7;

    /** The input JSON. */
    private final Reader in;

    /** True to accept non-spec compliant JSON */
    private boolean lenient = false;

    /**
     * Use a manual buffer to easily read and unread upcoming characters, and
     * also so we can create strings without an intermediate StringBuilder.
     * We decode literals directly out of this buffer, so it must be at least as
     * long as the longest token that can be reported as a number.
     */
    private final char[] buffer = new char[1024];
    private int pos = 0;
    private int limit = 0;

    private int lineNumber = 0;
    private int lineStart = 0;

    int peeked = PEEKED_NONE;

    /**
     * A peeked value that was composed entirely of digits with an optional
     * leading dash. Positive values may not have a leading 0.
     */
    private long peekedLong;

    /**
     * The number of characters in a peeked number literal. Increment 'pos' by
     * this after reading a number.
     */
    private int peekedNumberLength;

    /**
     * A peeked string that should be parsed on the next double, long or string.
     * This is populated before a numeric value is parsed and used if that parsing
     * fails.
     */
    private String peekedString;

    /*
     * The nesting stack. Using a manual array rather than an ArrayList saves 20%.
     */
    private int[] stack = new int[32];
    private int stackSize = 0;

    {
        stack[stackSize++] = SegmentJsonScope.EMPTY_DOCUMENT;
    }

    /*
     * The path members. It corresponds directly to stack: At indices where the
     * stack contains an object (EMPTY_OBJECT, DANGLING_NAME or NONEMPTY_OBJECT),
     * pathNames contains the name at this scope. Where it contains an array
     * (EMPTY_ARRAY, NONEMPTY_ARRAY) pathIndices contains the current index in
     * that array. Otherwise the value is undefined, and we take advantage of that
     * by incrementing pathIndices when doing so isn't useful.
     */
    private String[] pathNames = new String[32];
    private int[] pathIndices = new int[32];

    /**
     * Creates a new instance that reads a JSON-encoded stream from {@code in}.
     */
    public SegmentJsonReader(Reader in) {
        if (in == null) {
            throw new NullPointerException("in == null");
        }
        this.in = in;
    }

    /**
     * Configure this parser to be liberal in what it accepts. By default,
     * this parser is strict and only accepts JSON as specified by <a
     * href="http://www.ietf.org/rfc/rfc4627.txt">RFC 4627</a>. Setting the
     * parser to lenient causes it to ignore the following syntax errors:
     *
     * <ul>
     * <li>Streams that start with the <a href="#nonexecuteprefix">non-execute
     * prefix</a>, <code>")]}'\n"</code>.
     * <li>Streams that include multiple top-level values. With strict parsing,
     * each stream must contain exactly one top-level value.
     * <li>Top-level values of any type. With strict parsing, the top-level
     * value must be an object or an array.
     * <li>Numbers may be {@link Double#isNaN() NaNs} or {@link
     * Double#isInfinite() infinities}.
     * <li>End of line comments starting with {@code //} or {@code #} and
     * ending with a newline character.
     * <li>C-style comments starting with {@code /*} and ending with
     * {@code *}{@code /}. Such comments may not be nested.
     * <li>Names that are unquoted or {@code 'single quoted'}.
     * <li>Strings that are unquoted or {@code 'single quoted'}.
     * <li>Array elements separated by {@code ;} instead of {@code ,}.
     * <li>Unnecessary array separators. These are interpreted as if null
     * was the omitted value.
     * <li>Names and values separated by {@code =} or {@code =>} instead of
     * {@code :}.
     * <li>Name/value pairs separated by {@code ;} instead of {@code ,}.
     * </ul>
     */
    public final void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    /**
     * Returns true if this parser is liberal in what it accepts.
     */
    public final boolean isLenient() {
        return lenient;
    }

    /**
     * Consumes the next token from the JSON stream and asserts that it is the
     * beginning of a new array.
     */
    public void beginArray() throws IOException {
        int p = peeked;
        if (p == PEEKED_NONE) {
            p = doPeek();
        }
        if (p == PEEKED_BEGIN_ARRAY) {
            push(SegmentJsonScope.EMPTY_ARRAY);
            pathIndices[stackSize - 1] = 0;
            peeked = PEEKED_NONE;
        } else {
            throw new IllegalStateException("Expected BEGIN_ARRAY but was " + peek() + locationString());
        }
    }

    /**
     * Consumes the next token from the JSON stream and asserts that it is the
     * end of the current array.
     */
    public void endArray() throws IOException {
        int p = peeked;
        if (p == PEEKED_NONE) {
            p = doPeek();
        }
        if (p == PEEKED_END_ARRAY) {
            stackSize--;
            pathIndices[stackSize - 1]++;
            peeked = PEEKED_NONE;
        } else {
            throw new IllegalStateException("Expected END_ARRAY but was " + peek() + locationString());
        }
    }

    /**
     * Consumes the next token from the JSON stream and asserts that it is the
     * beginning of a new object.
     */
    public void beginObject() throws IOException {
        int p = peeked;
        if (p == PEEKED_NONE) {
            p = doPeek();
        }
        if (p == PEEKED_BEGIN_OBJECT) {
            push(SegmentJsonScope.EMPTY_OBJECT);
            peeked = PEEKED_NONE;
        } else {
            throw new IllegalStateException("Expected BEGIN_OBJECT but was " + peek() + locationString());
        }
    }

    /**
     * Consumes the next token from the JSON stream and asserts that it is the
     * end of the current object.
     */
    public void endObject() throws IOException {
        int p = peeked;
        if (p == PEEKED_NONE) {
            p = doPeek();
        }
        if (p == PEEKED_END_OBJECT) {
            stackSize--;
            pathNames[stackSize] = null; // Free the last path name so that it can be garbage collected!
            pathIndices[stackSize - 1]++;
            peeked = PEEKED_NONE;
        } else {
            throw new IllegalStateException("Expected END_OBJECT but was " + peek() + locationString());
        }
    }

    /**
     * Returns true if the current array or object has another element.
     */
    public boolean hasNext() throws IOException {
        int p = peeked;
        if (p == PEEKED_NONE) {
            p = doPeek();
        }
        return p != PEEKED_END_OBJECT && p != PEEKED_END_ARRAY;
    }

    /**
     * Returns the type of the next token without consuming it.
     */
    public JsonToken peek() throws IOException {
        int p = peeked;
        if (p == PEEKED_NONE) {
            p = doPeek();
        }

        switch (p) {
            case PEEKED_BEGIN_OBJECT:
                return JsonToken.BEGIN_OBJECT;
            case PEEKED_END_OBJECT:
                return JsonToken.END_OBJECT;
            case PEEKED_BEGIN_ARRAY:
                return JsonToken.BEGIN_ARRAY;
            case PEEKED_END_ARRAY:
                return JsonToken.END_ARRAY;
            case PEEKED_SINGLE_QUOTED_NAME:
            case PEEKED_DOUBLE_QUOTED_NAME:
            case PEEKED_UNQUOTED_NAME:
                return JsonToken.NAME;
            case PEEKED_TRUE:
            case PEEKED_FALSE:
                return JsonToken.BOOLEAN;
            case PEEKED_NULL:
                return JsonToken.NULL;
            case PEEKED_SINGLE_QUOTED:
            case PEEKED_DOUBLE_QUOTED:
            case PEEKED_UNQUOTED:
            case PEEKED_BUFFERED:
                return JsonToken.STRING;
            case PEEKED_LONG:
            case PEEKED_NUMBER:
                return JsonToken.NUMBER;
            case PEEKED_EOF:
                return JsonToken.END_DOCUMENT;
            default:
                throw new AssertionError();
        }
    }

    int doPeek() throws IOException {
        int peekStack = stack[stackSize - 1];
        if (peekStack == SegmentJsonScope.EMPTY_ARRAY) {
            stack[stackSize - 1] = SegmentJsonScope.NONEMPTY_ARRAY;
        } else if (peekStack == SegmentJsonScope.NONEMPTY_ARRAY) {
            // Look for a comma before the next element.
            int c = nextNonWhitespace(true);
            switch (c) {
                case ']':
                    return peeked = PEEKED_END_ARRAY;
                case ';':
                    checkLenient(); // fall-through
                case ',':
                    break;
                default:
                    throw syntaxError("Unterminated array");
            }
        } else if (peekStack == SegmentJsonScope.EMPTY_OBJECT || peekStack == SegmentJsonScope.NONEMPTY_OBJECT) {
            stack[stackSize - 1] = SegmentJsonScope.DANGLING_NAME;
            // Look for a comma before the next element.
            if (peekStack == SegmentJsonScope.NONEMPTY_OBJECT) {
                int c = nextNonWhitespace(true);
                switch (c) {
                    case '}':
                        return peeked = PEEKED_END_OBJECT;
                    case ';':
                        checkLenient(); // fall-through
                    case ',':
                        break;
                    default:
                        throw syntaxError("Unterminated object");
                }
            }
            int c = nextNonWhitespace(true);
            switch (c) {
                case '"':
                    return peeked = PEEKED_DOUBLE_QUOTED_NAME;
                case '\'':
                    checkLenient();
                    return peeked = PEEKED_SINGLE_QUOTED_NAME;
                case '}':
                    if (peekStack != SegmentJsonScope.NONEMPTY_OBJECT) {
                        return peeked = PEEKED_END_OBJECT;
                    } else {
                        throw syntaxError("Expected name");
                    }
                default:
                    checkLenient();
                    pos--; // Don't consume the first character in an unquoted string.
                    if (isLiteral((char)c)) {
                        return peeked = PEEKED_UNQUOTED_NAME;
                    } else {
                        throw syntaxError("Expected name");
                    }
            }
        } else if (peekStack == SegmentJsonScope.DANGLING_NAME) {
            stack[stackSize - 1] = SegmentJsonScope.NONEMPTY_OBJECT;
            // Look for a colon before the value.
            int c = nextNonWhitespace(true);
            switch (c) {
                case ':':
                    break;
                case '=':
                    checkLenient();
                    if ((pos < limit || fillBuffer(1)) && buffer[pos] == '>') {
                        pos++;
                    }
                    break;
                default:
                    throw syntaxError("Expected ':'");
            }
        } else if (peekStack == SegmentJsonScope.EMPTY_DOCUMENT) {
            if (lenient) {
                consumeNonExecutePrefix();
            }
            stack[stackSize - 1] = SegmentJsonScope.NONEMPTY_DOCUMENT;
        } else if (peekStack == SegmentJsonScope.NONEMPTY_DOCUMENT) {
            int c = nextNonWhitespace(false);
            if (c == -1) {
                return peeked = PEEKED_EOF;
            } else {
                checkLenient();
                pos--;
            }
        } else if (peekStack == SegmentJsonScope.CLOSED) {
            throw new IllegalStateException("JsonReader is closed");
        }

        int c = nextNonWhitespace(true);
        switch (c) {
            case ']':
                if (peekStack == SegmentJsonScope.EMPTY_ARRAY) {
                    return peeked = PEEKED_END_ARRAY;
                }
                // fall-through to handle ",]"
            case ';':
            case ',':
                // In lenient mode, a 0-length literal in an array means 'null'.
                if (peekStack == SegmentJsonScope.EMPTY_ARRAY || peekStack == SegmentJsonScope.NONEMPTY_ARRAY) {
                    checkLenient();
                    pos--;
                    return peeked = PEEKED_NULL;
                } else {
                    throw syntaxError("Unexpected value");
                }
            case '\'':
                checkLenient();
                return peeked = PEEKED_SINGLE_QUOTED;
            case '"':
                return peeked = PEEKED_DOUBLE_QUOTED;
            case '[':
                return peeked = PEEKED_BEGIN_ARRAY;
            case '{':
                return peeked = PEEKED_BEGIN_OBJECT;
            default:
                pos--; // Don't consume the first character in a literal value.
        }

        int result = peekKeyword();
        if (result != PEEKED_NONE) {
            return result;
        }

        result = peekNumber();
        if (result != PEEKED_NONE) {
            return result;
        }

        if (!isLiteral(buffer[pos])) {
            throw syntaxError("Expected value");
        }

        checkLenient();
        return peeked = PEEKED_UNQUOTED;
    }

    private int peekKeyword() throws IOException {
        // Figure out which keyword we're matching against by its first character.
        char c = buffer[pos];
        String keyword;
        String keywordUpper;
        int peeking;
        if (c == 't' || c == 'T') {
            keyword = "true";
            keywordUpper = "TRUE";
            peeking = PEEKED_TRUE;
        } else if (c == 'f' || c == 'F') {
            keyword = "false";
            keywordUpper = "FALSE";
            peeking = PEEKED_FALSE;
        } else if (c == 'n' || c == 'N') {
            keyword = "null";
            keywordUpper = "NULL";
            peeking = PEEKED_NULL;
        } else {
            return PEEKED_NONE;
        }

        // Confirm that chars [1..length) match the keyword.
        int length = keyword.length();
        for (int i = 1; i < length; i++) {
            if (pos + i >= limit && !fillBuffer(i + 1)) {
                return PEEKED_NONE;
            }
            c = buffer[pos + i];
            if (c != keyword.charAt(i) && c != keywordUpper.charAt(i)) {
                return PEEKED_NONE;
            }
        }

        if ((pos + length < limit || fillBuffer(length + 1))
            && isLiteral(buffer[pos + length])) {
            return PEEKED_NONE; // Don't match trues, falsey or nullsoft!
        }

        // We've found the keyword followed either by EOF or by a non-literal character.
        pos += length;
        return peeked = peeking;
    }

    private int peekNumber() throws IOException {
        // Like nextNonWhitespace, this uses locals 'p' and 'l' to save inner-loop field access.
        char[] buffer = this.buffer;
        int p = pos;
        int l = limit;

        long value = 0; // Negative to accommodate Long.MIN_VALUE more easily.
        boolean negative = false;
        boolean fitsInLong = true;
        int last = NUMBER_CHAR_NONE;

        int i = 0;

        charactersOfNumber:
        for (; true; i++) {
            if (p + i == l) {
                if (i == buffer.length) {
                    // Though this looks like a well-formed number, it's too long to continue reading. Give up
                    // and let the application handle this as an unquoted literal.
                    return PEEKED_NONE;
                }
                if (!fillBuffer(i + 1)) {
                    break;
                }
                p = pos;
                l = limit;
            }

            char c = buffer[p + i];
            switch (c) {
                case '-':
                    if (last == NUMBER_CHAR_NONE) {
                        negative = true;
                        last = NUMBER_CHAR_SIGN;
                        continue;
                    } else if (last == NUMBER_CHAR_EXP_E) {
                        last = NUMBER_CHAR_EXP_SIGN;
                        continue;
                    }
                    return PEEKED_NONE;

                case '+':
                    if (last == NUMBER_CHAR_EXP_E) {
                        last = NUMBER_CHAR_EXP_SIGN;
                        continue;
                    }
                    return PEEKED_NONE;

                case 'e':
                case 'E':
                    if (last == NUMBER_CHAR_DIGIT || last == NUMBER_CHAR_FRACTION_DIGIT) {
                        last = NUMBER_CHAR_EXP_E;
                        continue;
                    }
                    return PEEKED_NONE;

                case '.':
                    if (last == NUMBER_CHAR_DIGIT) {
                        last = NUMBER_CHAR_DECIMAL;
                        continue;
                    }
                    return PEEKED_NONE;

                default:
                    if (c < '0' || c > '9') {
                        if (!isLiteral(c)) {
                            break charactersOfNumber;
                        }
                        return PEEKED_NONE;
                    }
                    if (last == NUMBER_CHAR_SIGN || last == NUMBER_CHAR_NONE) {
                        value = -(c - '0');
                        last = NUMBER_CHAR_DIGIT;
                    } else if (last == NUMBER_CHAR_DIGIT) {
                        if (value == 0) {
                            return PEEKED_NONE; // Leading '0' prefix is not allowed (since it could be octal).
                        }
                        long newValue = value * 10 - (c - '0');
                        fitsInLong &= value > MIN_INCOMPLETE_INTEGER
                            || (value == MIN_INCOMPLETE_INTEGER && newValue < value);
                        value = newValue;
                    } else if (last == NUMBER_CHAR_DECIMAL) {
                        last = NUMBER_CHAR_FRACTION_DIGIT;
                    } else if (last == NUMBER_CHAR_EXP_E || last == NUMBER_CHAR_EXP_SIGN) {
                        last = NUMBER_CHAR_EXP_DIGIT;
                    }
            }
        }

        // We've read a complete number. Decide if it's a PEEKED_LONG or a PEEKED_NUMBER.
        if (last == NUMBER_CHAR_DIGIT && fitsInLong && (value != Long.MIN_VALUE || negative)) {
            peekedLong = negative ? value : -value;
            pos += i;
            return peeked = PEEKED_LONG;
        } else if (last == NUMBER_CHAR_DIGIT || last == NUMBER_CHAR_FRACTION_DIGIT
            || last == NUMBER_CHAR_EXP_DIGIT) {
            peekedNumberLength = i;
            return peeked = PEEKED_NUMBER;
        } else {
            return PEEKED_NONE;
        }
    }

    private boolean isLiteral(char c) throws IOException {
        switch (c) {
            case '/':
            case '\\':
            case ';':
            case '#':
            case '=':
                checkLenient(); // fall-through
            case '{':
            case '}':
            case '[':
            case ']':
            case ':':
            case ',':
            case ' ':
            case '\t':
            case '\f':
            case '\r':
            case '\n':
                return false;
            default:
                return true;
        }
    }

    /**
     * Returns the next token, a {@link com.google.gson.stream.JsonToken#NAME property name}, and
     * consumes it.
     *
     * @throws java.io.IOException if the next token in the stream is not a property name.
     */
    public String nextName() throws IOException {
        int p = peeked;
        if (p == PEEKED_NONE) {
            p = doPeek();
        }
        String result;
        if (p == PEEKED_UNQUOTED_NAME) {
            result = nextUnquotedValue();
        } else if (p == PEEKED_SINGLE_QUOTED_NAME) {
            result = nextQuotedValue('\'');
        } else if (p == PEEKED_DOUBLE_QUOTED_NAME) {
            result = nextQuotedValue('"');
        } else {
            throw new IllegalStateException("Expected a name but was " + peek() + locationString());
        }
        peeked = PEEKED_NONE;
        pathNames[stackSize - 1] = result;
        return result;
    }

    /**
     * Returns the {@link com.google.gson.stream.JsonToken#STRING string} value of the next token,
     * consuming it. If the next token is a number, this method will return its
     * string form.
     *
     * @throws IllegalStateException if the next token is not a string or if this reader is closed.
     */
    public StringValue nextString() throws IOException {
        int p = peeked;
        if (p == PEEKED_NONE) {
            p = doPeek();
        }
        StringValue result = new StringValue();
        if (p == PEEKED_UNQUOTED) {
            result.setNonQuoteValue(nextUnquotedValue());
            result.setQuoteValue(nextUnquotedValue());
        } else if (p == PEEKED_SINGLE_QUOTED) {
            result.setNonQuoteValue(nextQuotedValue('\''));
            result.setQuoteValue(nextQuotedValue('\''));
        } else if (p == PEEKED_DOUBLE_QUOTED) {
            result = nextQuotedValueForString('"');
        } else if (p == PEEKED_BUFFERED) {
            result.setNonQuoteValue(peekedString);
            result.setQuoteValue(peekedString);
            peekedString = null;
        } else if (p == PEEKED_LONG) {
            result.setNonQuoteValue(Long.toString(peekedLong));
            result.setQuoteValue(Long.toString(peekedLong));
        } else if (p == PEEKED_NUMBER) {
            result.setNonQuoteValue(new String(buffer, pos, peekedNumberLength));
            result.setQuoteValue(new String(buffer, pos, peekedNumberLength));
            pos += peekedNumberLength;
        } else {
            throw new IllegalStateException("Expected a string but was " + peek() + locationString());
        }
        peeked = PEEKED_NONE;
        pathIndices[stackSize - 1]++;
        return result;
    }

    /**
     * Returns the {@link com.google.gson.stream.JsonToken#BOOLEAN boolean} value of the next token,
     * consuming it.
     *
     * @throws IllegalStateException if the next token is not a boolean or if this reader is closed.
     */
    public boolean nextBoolean() throws IOException {
        int p = peeked;
        if (p == PEEKED_NONE) {
            p = doPeek();
        }
        if (p == PEEKED_TRUE) {
            peeked = PEEKED_NONE;
            pathIndices[stackSize - 1]++;
            return true;
        } else if (p == PEEKED_FALSE) {
            peeked = PEEKED_NONE;
            pathIndices[stackSize - 1]++;
            return false;
        }
        throw new IllegalStateException("Expected a boolean but was " + peek() + locationString());
    }

    /**
     * Consumes the next token from the JSON stream and asserts that it is a
     * literal null.
     *
     * @throws IllegalStateException if the next token is not null or if this reader is closed.
     */
    public void nextNull() throws IOException {
        int p = peeked;
        if (p == PEEKED_NONE) {
            p = doPeek();
        }
        if (p == PEEKED_NULL) {
            peeked = PEEKED_NONE;
            pathIndices[stackSize - 1]++;
        } else {
            throw new IllegalStateException("Expected null but was " + peek() + locationString());
        }
    }

    /**
     * Returns the {@link com.google.gson.stream.JsonToken#NUMBER double} value of the next token,
     * consuming it. If the next token is a string, this method will attempt to
     * parse it as a double using {@link Double#parseDouble(String)}.
     *
     * @throws IllegalStateException if the next token is not a literal value.
     * @throws NumberFormatException if the next literal value cannot be parsed as a double, or is non-finite.
     */
    public double nextDouble() throws IOException {
        int p = peeked;
        if (p == PEEKED_NONE) {
            p = doPeek();
        }

        if (p == PEEKED_LONG) {
            peeked = PEEKED_NONE;
            pathIndices[stackSize - 1]++;
            return (double)peekedLong;
        }

        if (p == PEEKED_NUMBER) {
            peekedString = new String(buffer, pos, peekedNumberLength);
            pos += peekedNumberLength;
        } else if (p == PEEKED_SINGLE_QUOTED || p == PEEKED_DOUBLE_QUOTED) {
            peekedString = nextQuotedValue(p == PEEKED_SINGLE_QUOTED ? '\'' : '"');
        } else if (p == PEEKED_UNQUOTED) {
            peekedString = nextUnquotedValue();
        } else if (p != PEEKED_BUFFERED) {
            throw new IllegalStateException("Expected a double but was " + peek() + locationString());
        }

        peeked = PEEKED_BUFFERED;
        double result = Double.parseDouble(peekedString); // don't catch this NumberFormatException.
        if (!lenient && (Double.isNaN(result) || Double.isInfinite(result))) {
            throw new MalformedJsonException(
                "JSON forbids NaN and infinities: " + result + locationString());
        }
        peekedString = null;
        peeked = PEEKED_NONE;
        pathIndices[stackSize - 1]++;
        return result;
    }

    /**
     * Returns the {@link com.google.gson.stream.JsonToken#NUMBER long} value of the next token,
     * consuming it. If the next token is a string, this method will attempt to
     * parse it as a long. If the next token's numeric value cannot be exactly
     * represented by a Java {@code long}, this method throws.
     *
     * @throws IllegalStateException if the next token is not a literal value.
     * @throws NumberFormatException if the next literal value cannot be parsed as a number, or exactly represented as a
     * long.
     */
    public long nextLong() throws IOException {
        int p = peeked;
        if (p == PEEKED_NONE) {
            p = doPeek();
        }

        if (p == PEEKED_LONG) {
            peeked = PEEKED_NONE;
            pathIndices[stackSize - 1]++;
            return peekedLong;
        }

        if (p == PEEKED_NUMBER) {
            peekedString = new String(buffer, pos, peekedNumberLength);
            pos += peekedNumberLength;
        } else if (p == PEEKED_SINGLE_QUOTED || p == PEEKED_DOUBLE_QUOTED || p == PEEKED_UNQUOTED) {
            if (p == PEEKED_UNQUOTED) {
                peekedString = nextUnquotedValue();
            } else {
                peekedString = nextQuotedValue(p == PEEKED_SINGLE_QUOTED ? '\'' : '"');
            }
            try {
                long result = Long.parseLong(peekedString);
                peeked = PEEKED_NONE;
                pathIndices[stackSize - 1]++;
                return result;
            } catch (NumberFormatException ignored) {
                // Fall back to parse as a double below.
            }
        } else {
            throw new IllegalStateException("Expected a long but was " + peek() + locationString());
        }

        peeked = PEEKED_BUFFERED;
        double asDouble = Double.parseDouble(peekedString); // don't catch this NumberFormatException.
        long result = (long)asDouble;
        if (result != asDouble) { // Make sure no precision was lost casting to 'long'.
            throw new NumberFormatException("Expected a long but was " + peekedString + locationString());
        }
        peekedString = null;
        peeked = PEEKED_NONE;
        pathIndices[stackSize - 1]++;
        return result;
    }

    /**
     * Returns the string up to but not including {@code quote}, unescaping any
     * character escape sequences encountered along the way. The opening quote
     * should have already been read. This consumes the closing quote, but does
     * not include it in the returned string.
     *
     * @param quote either ' or ".
     * @throws NumberFormatException if any unicode escape sequences are malformed.
     */
    private String nextQuotedValue(char quote) throws IOException {
        // Like nextNonWhitespace, this uses locals 'p' and 'l' to save inner-loop field access.
        char[] buffer = this.buffer;
        StringBuilder builder = new StringBuilder();
        while (true) {
            int p = pos;
            int l = limit;
      /* the index of the first character not yet appended to the builder. */
            int start = p;
            while (p < l) {
                int c = buffer[p++];

                if (c == quote) {
                    pos = p;
                    builder.append(buffer, start, p - start - 1);
                    return builder.toString();
                } else if (c == '\\') {
                    pos = p;
                    builder.append(buffer, start, p - start - 1);
                    builder.append(readEscapeCharacter());
                    p = pos;
                    l = limit;
                    start = p;
                } else if (c == '\n') {
                    lineNumber++;
                    lineStart = p;
                }
            }

            builder.append(buffer, start, p - start);
            pos = p;
            if (!fillBuffer(1)) {
                throw syntaxError("Unterminated string");
            }
        }
    }

    private StringValue nextQuotedValueForString(char quote) throws IOException {
        // Like nextNonWhitespace, this uses locals 'p' and 'l' to save inner-loop field access.
        StringValue result = new StringValue();
        char[] buffer = this.buffer;
        StringBuilder nonQuoteBuilder = new StringBuilder();
        StringBuilder quoteBuilder = new StringBuilder();
        while (true) {
            int p = pos;
            int l = limit;
      /* the index of the first character not yet appended to the builder. */
            int start = p;
            while (p < l) {
                int c = buffer[p++];

                if (c == quote) {
                    pos = p;
                    nonQuoteBuilder.append(buffer, start, p - start - 1);
                    quoteBuilder.append(buffer, start, p - start - 1);

                    result.setNonQuoteValue(nonQuoteBuilder.toString());
                    result.setQuoteValue(quoteBuilder.toString());
                    return result;
                } else if (c == '\\') {
                    pos = p;
                    nonQuoteBuilder.append(buffer, start, p - start - 1);
                    quoteBuilder.append(buffer, start, p - start - 1);

                    char character = readEscapeCharacter();
                    nonQuoteBuilder.append(character);

                    switch (character) {
                        case '\'':
                        case '"':
                        case '\\':
                        case '/':
                            quoteBuilder.append('\\');
                        default:
                            quoteBuilder.append(character);
                    }

                    p = pos;
                    l = limit;
                    start = p;
                } else if (c == '\n') {
                    lineNumber++;
                    lineStart = p;
                }
            }

            nonQuoteBuilder.append(buffer, start, p - start);
            quoteBuilder.append(buffer, start, p - start);
            pos = p;
            if (!fillBuffer(1)) {
                throw syntaxError("Unterminated string");
            }
        }
    }

    /**
     * Returns an unquoted value as a string.
     */
    @SuppressWarnings("fallthrough")
    private String nextUnquotedValue() throws IOException {
        StringBuilder builder = null;
        int i = 0;

        findNonLiteralCharacter:
        while (true) {
            for (; pos + i < limit; i++) {
                switch (buffer[pos + i]) {
                    case '/':
                    case '\\':
                    case ';':
                    case '#':
                    case '=':
                        checkLenient(); // fall-through
                    case '{':
                    case '}':
                    case '[':
                    case ']':
                    case ':':
                    case ',':
                    case ' ':
                    case '\t':
                    case '\f':
                    case '\r':
                    case '\n':
                        break findNonLiteralCharacter;
                }
            }

            // Attempt to load the entire literal into the buffer at once.
            if (i < buffer.length) {
                if (fillBuffer(i + 1)) {
                    continue;
                } else {
                    break;
                }
            }

            // use a StringBuilder when the value is too long. This is too long to be a number!
            if (builder == null) {
                builder = new StringBuilder();
            }
            builder.append(buffer, pos, i);
            pos += i;
            i = 0;
            if (!fillBuffer(1)) {
                break;
            }
        }

        String result;
        if (builder == null) {
            result = new String(buffer, pos, i);
        } else {
            builder.append(buffer, pos, i);
            result = builder.toString();
        }
        pos += i;
        return result;
    }

    private void skipQuotedValue(char quote) throws IOException {
        // Like nextNonWhitespace, this uses locals 'p' and 'l' to save inner-loop field access.
        char[] buffer = this.buffer;
        do {
            int p = pos;
            int l = limit;
      /* the index of the first character not yet appended to the builder. */
            while (p < l) {
                int c = buffer[p++];
                if (c == quote) {
                    pos = p;
                    return;
                } else if (c == '\\') {
                    pos = p;
                    readEscapeCharacter();
                    p = pos;
                    l = limit;
                } else if (c == '\n') {
                    lineNumber++;
                    lineStart = p;
                }
            }
            pos = p;
        }
        while (fillBuffer(1));
        throw syntaxError("Unterminated string");
    }

    private void skipUnquotedValue() throws IOException {
        do {
            int i = 0;
            for (; pos + i < limit; i++) {
                switch (buffer[pos + i]) {
                    case '/':
                    case '\\':
                    case ';':
                    case '#':
                    case '=':
                        checkLenient(); // fall-through
                    case '{':
                    case '}':
                    case '[':
                    case ']':
                    case ':':
                    case ',':
                    case ' ':
                    case '\t':
                    case '\f':
                    case '\r':
                    case '\n':
                        pos += i;
                        return;
                }
            }
            pos += i;
        }
        while (fillBuffer(1));
    }

    /**
     * Returns the {@link com.google.gson.stream.JsonToken#NUMBER int} value of the next token,
     * consuming it. If the next token is a string, this method will attempt to
     * parse it as an int. If the next token's numeric value cannot be exactly
     * represented by a Java {@code int}, this method throws.
     *
     * @throws IllegalStateException if the next token is not a literal value.
     * @throws NumberFormatException if the next literal value cannot be parsed as a number, or exactly represented as
     * an int.
     */
    public int nextInt() throws IOException {
        int p = peeked;
        if (p == PEEKED_NONE) {
            p = doPeek();
        }

        int result;
        if (p == PEEKED_LONG) {
            result = (int)peekedLong;
            if (peekedLong != result) { // Make sure no precision was lost casting to 'int'.
                throw new NumberFormatException("Expected an int but was " + peekedLong + locationString());
            }
            peeked = PEEKED_NONE;
            pathIndices[stackSize - 1]++;
            return result;
        }

        if (p == PEEKED_NUMBER) {
            peekedString = new String(buffer, pos, peekedNumberLength);
            pos += peekedNumberLength;
        } else if (p == PEEKED_SINGLE_QUOTED || p == PEEKED_DOUBLE_QUOTED || p == PEEKED_UNQUOTED) {
            if (p == PEEKED_UNQUOTED) {
                peekedString = nextUnquotedValue();
            } else {
                peekedString = nextQuotedValue(p == PEEKED_SINGLE_QUOTED ? '\'' : '"');
            }
            try {
                result = Integer.parseInt(peekedString);
                peeked = PEEKED_NONE;
                pathIndices[stackSize - 1]++;
                return result;
            } catch (NumberFormatException ignored) {
                // Fall back to parse as a double below.
            }
        } else {
            throw new IllegalStateException("Expected an int but was " + peek() + locationString());
        }

        peeked = PEEKED_BUFFERED;
        double asDouble = Double.parseDouble(peekedString); // don't catch this NumberFormatException.
        result = (int)asDouble;
        if (result != asDouble) { // Make sure no precision was lost casting to 'int'.
            throw new NumberFormatException("Expected an int but was " + peekedString + locationString());
        }
        peekedString = null;
        peeked = PEEKED_NONE;
        pathIndices[stackSize - 1]++;
        return result;
    }

    /**
     * Closes this JSON reader and the underlying {@link java.io.Reader}.
     */
    public void close() throws IOException {
        peeked = PEEKED_NONE;
        stack[0] = SegmentJsonScope.CLOSED;
        stackSize = 1;
        in.close();
    }

    /**
     * Skips the next value recursively. If it is an object or array, all nested
     * elements are skipped. This method is intended for use when the JSON token
     * stream contains unrecognized or unhandled values.
     */
    public void skipValue() throws IOException {
        int count = 0;
        do {
            int p = peeked;
            if (p == PEEKED_NONE) {
                p = doPeek();
            }

            if (p == PEEKED_BEGIN_ARRAY) {
                push(SegmentJsonScope.EMPTY_ARRAY);
                count++;
            } else if (p == PEEKED_BEGIN_OBJECT) {
                push(SegmentJsonScope.EMPTY_OBJECT);
                count++;
            } else if (p == PEEKED_END_ARRAY) {
                stackSize--;
                count--;
            } else if (p == PEEKED_END_OBJECT) {
                stackSize--;
                count--;
            } else if (p == PEEKED_UNQUOTED_NAME || p == PEEKED_UNQUOTED) {
                skipUnquotedValue();
            } else if (p == PEEKED_SINGLE_QUOTED || p == PEEKED_SINGLE_QUOTED_NAME) {
                skipQuotedValue('\'');
            } else if (p == PEEKED_DOUBLE_QUOTED || p == PEEKED_DOUBLE_QUOTED_NAME) {
                skipQuotedValue('"');
            } else if (p == PEEKED_NUMBER) {
                pos += peekedNumberLength;
            }
            peeked = PEEKED_NONE;
        }
        while (count != 0);

        pathIndices[stackSize - 1]++;
        pathNames[stackSize - 1] = "null";
    }

    private void push(int newTop) {
        if (stackSize == stack.length) {
            int[] newStack = new int[stackSize * 2];
            int[] newPathIndices = new int[stackSize * 2];
            String[] newPathNames = new String[stackSize * 2];
            System.arraycopy(stack, 0, newStack, 0, stackSize);
            System.arraycopy(pathIndices, 0, newPathIndices, 0, stackSize);
            System.arraycopy(pathNames, 0, newPathNames, 0, stackSize);
            stack = newStack;
            pathIndices = newPathIndices;
            pathNames = newPathNames;
        }
        stack[stackSize++] = newTop;
    }

    /**
     * Returns true once {@code limit - pos >= minimum}. If the data is
     * exhausted before that many characters are available, this returns
     * false.
     */
    private boolean fillBuffer(int minimum) throws IOException {
        char[] buffer = this.buffer;
        lineStart -= pos;
        if (limit != pos) {
            limit -= pos;
            System.arraycopy(buffer, pos, buffer, 0, limit);
        } else {
            limit = 0;
        }

        pos = 0;
        int total;
        while ((total = in.read(buffer, limit, buffer.length - limit)) != -1) {
            limit += total;

            // if this is the first read, consume an optional byte order mark (BOM) if it exists
            if (lineNumber == 0 && lineStart == 0 && limit > 0 && buffer[0] == '\ufeff') {
                pos++;
                lineStart++;
                minimum++;
            }

            if (limit >= minimum) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the next character in the stream that is neither whitespace nor a
     * part of a comment. When this returns, the returned character is always at
     * {@code buffer[pos-1]}; this means the caller can always push back the
     * returned character by decrementing {@code pos}.
     */
    private int nextNonWhitespace(boolean throwOnEof) throws IOException {
    /*
     * This code uses ugly local variables 'p' and 'l' representing the 'pos'
     * and 'limit' fields respectively. Using locals rather than fields saves
     * a few field reads for each whitespace character in a pretty-printed
     * document, resulting in a 5% speedup. We need to flush 'p' to its field
     * before any (potentially indirect) call to fillBuffer() and reread both
     * 'p' and 'l' after any (potentially indirect) call to the same method.
     */
        char[] buffer = this.buffer;
        int p = pos;
        int l = limit;
        while (true) {
            if (p == l) {
                pos = p;
                if (!fillBuffer(1)) {
                    break;
                }
                p = pos;
                l = limit;
            }

            int c = buffer[p++];
            if (c == '\n') {
                lineNumber++;
                lineStart = p;
                continue;
            } else if (c == ' ' || c == '\r' || c == '\t') {
                continue;
            }

            if (c == '/') {
                pos = p;
                if (p == l) {
                    pos--; // push back '/' so it's still in the buffer when this method returns
                    boolean charsLoaded = fillBuffer(2);
                    pos++; // consume the '/' again
                    if (!charsLoaded) {
                        return c;
                    }
                }

                checkLenient();
                char peek = buffer[pos];
                switch (peek) {
                    case '*':
                        // skip a /* c-style comment */
                        pos++;
                        if (!skipTo("*/")) {
                            throw syntaxError("Unterminated comment");
                        }
                        p = pos + 2;
                        l = limit;
                        continue;

                    case '/':
                        // skip a // end-of-line comment
                        pos++;
                        skipToEndOfLine();
                        p = pos;
                        l = limit;
                        continue;

                    default:
                        return c;
                }
            } else if (c == '#') {
                pos = p;
        /*
         * Skip a # hash end-of-line comment. The JSON RFC doesn't
         * specify this behaviour, but it's required to parse
         * existing documents. See http://b/2571423.
         */
                checkLenient();
                skipToEndOfLine();
                p = pos;
                l = limit;
            } else {
                pos = p;
                return c;
            }
        }
        if (throwOnEof) {
            throw new EOFException("End of input" + locationString());
        } else {
            return -1;
        }
    }

    private void checkLenient() throws IOException {
        if (!lenient) {
            throw syntaxError("Use JsonReader.setLenient(true) to accept malformed JSON");
        }
    }

    /**
     * Advances the position until after the next newline character. If the line
     * is terminated by "\r\n", the '\n' must be consumed as whitespace by the
     * caller.
     */
    private void skipToEndOfLine() throws IOException {
        while (pos < limit || fillBuffer(1)) {
            char c = buffer[pos++];
            if (c == '\n') {
                lineNumber++;
                lineStart = pos;
                break;
            } else if (c == '\r') {
                break;
            }
        }
    }

    /**
     * @param toFind a string to search for. Must not contain a newline.
     */
    private boolean skipTo(String toFind) throws IOException {
        outer:
        for (; pos + toFind.length() <= limit || fillBuffer(toFind.length()); pos++) {
            if (buffer[pos] == '\n') {
                lineNumber++;
                lineStart = pos + 1;
                continue;
            }
            for (int c = 0; c < toFind.length(); c++) {
                if (buffer[pos + c] != toFind.charAt(c)) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    @Override public String toString() {
        return getClass().getSimpleName() + locationString();
    }

    private String locationString() {
        int line = lineNumber + 1;
        int column = pos - lineStart + 1;
        return " at line " + line + " column " + column + " path " + getPath();
    }

    /**
     * Returns a <a href="http://goessner.net/articles/JsonPath/">JsonPath</a> to
     * the current location in the JSON value.
     */
    public String getPath() {
        StringBuilder result = new StringBuilder().append('$');
        for (int i = 0, size = stackSize; i < size; i++) {
            switch (stack[i]) {
                case SegmentJsonScope.EMPTY_ARRAY:
                case SegmentJsonScope.NONEMPTY_ARRAY:
                    result.append('[').append(pathIndices[i]).append(']');
                    break;

                case SegmentJsonScope.EMPTY_OBJECT:
                case SegmentJsonScope.DANGLING_NAME:
                case SegmentJsonScope.NONEMPTY_OBJECT:
                    result.append('.');
                    if (pathNames[i] != null) {
                        result.append(pathNames[i]);
                    }
                    break;

                case SegmentJsonScope.NONEMPTY_DOCUMENT:
                case SegmentJsonScope.EMPTY_DOCUMENT:
                case SegmentJsonScope.CLOSED:
                    break;
            }
        }
        return result.toString();
    }

    /**
     * Unescapes the character identified by the character or characters that
     * immediately follow a backslash. The backslash '\' should have already
     * been read. This supports both unicode escapes "u000A" and two-character
     * escapes "\n".
     *
     * @throws NumberFormatException if any unicode escape sequences are malformed.
     */
    private char readEscapeCharacter() throws IOException {
        if (pos == limit && !fillBuffer(1)) {
            throw syntaxError("Unterminated escape sequence");
        }

        char escaped = buffer[pos++];
        switch (escaped) {
            case 'u':
                if (pos + 4 > limit && !fillBuffer(4)) {
                    throw syntaxError("Unterminated escape sequence");
                }
                // Equivalent to Integer.parseInt(stringPool.get(buffer, pos, 4), 16);
                char result = 0;
                for (int i = pos, end = i + 4; i < end; i++) {
                    char c = buffer[i];
                    result <<= 4;
                    if (c >= '0' && c <= '9') {
                        result += c - '0';
                    } else if (c >= 'a' && c <= 'f') {
                        result += c - 'a' + 10;
                    } else if (c >= 'A' && c <= 'F') {
                        result += c - 'A' + 10;
                    } else {
                        throw new NumberFormatException("\\u" + new String(buffer, pos, 4));
                    }
                }
                pos += 4;
                return result;

            case 't':
                return '\t';

            case 'b':
                return '\b';

            case 'n':
                return '\n';

            case 'r':
                return '\r';

            case 'f':
                return '\f';

            case '\n':
                lineNumber++;
                lineStart = pos;
                // fall-through

            case '\'':
            case '"':
            case '\\':
            case '/':
                return escaped;
            default:
                // throw error when none of the above cases are matched
                throw syntaxError("Invalid escape sequence");
        }
    }

    /**
     * Throws a new IO exception with the given message and a context snippet
     * with this reader's content.
     */
    private IOException syntaxError(String message) throws IOException {
        throw new MalformedJsonException(message + locationString());
    }

    /**
     * Consumes the non-execute prefix if it exists.
     */
    private void consumeNonExecutePrefix() throws IOException {
        // fast forward through the leading whitespace
        nextNonWhitespace(true);
        pos--;

        if (pos + NON_EXECUTE_PREFIX.length > limit && !fillBuffer(NON_EXECUTE_PREFIX.length)) {
            return;
        }

        for (int i = 0; i < NON_EXECUTE_PREFIX.length; i++) {
            if (buffer[pos + i] != NON_EXECUTE_PREFIX[i]) {
                return; // not a security token!
            }
        }

        // we consumed a security token!
        pos += NON_EXECUTE_PREFIX.length;
    }

    public static class StringValue {
        private String nonQuoteValue;
        private String quoteValue;

        public String getNonQuoteValue() {
            return nonQuoteValue;
        }

        public void setNonQuoteValue(String nonQuoteValue) {
            this.nonQuoteValue = nonQuoteValue;
        }

        public String getQuoteValue() {
            return quoteValue;
        }

        public void setQuoteValue(String quoteValue) {
            this.quoteValue = quoteValue;
        }
    }
}