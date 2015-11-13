package com.ai.cloud.skywalking.context;

import java.util.Stack;

public class Context {
    private static ThreadLocal<SpanNodeStack> nodes = new ThreadLocal<SpanNodeStack>();

    private Context() {

    }

    public static void append(Span span) {
        if (nodes.get() == null) {
            nodes.set(new SpanNodeStack());
        }
        nodes.get().push(span);
    }

    public static Span getLastSpan() {
        if (nodes.get() == null) {
            return null;
        }
        return nodes.get().peek();
    }

    public static Span removeLastSpan() {
        if (nodes.get() == null)
            return null;
        return nodes.get().pop();
    }

    static class SpanNodeStack {
        private Stack<SpanNode> spans = new Stack<SpanNode>();

        public Span pop() {
            Span span = spans.pop().getData();
            if (!isEmpty()) {
                spans.peek().incrementNextSubSpanLevelId();
            }
            return span;
        }

        public void push(Span span) {
            if (!isEmpty()) {
                spans.push(new SpanNode(span, spans.peek().getNextSubSpanLevelId()));
            } else {
                spans.push(new SpanNode(span));
            }

        }

        public Span peek() {
            if (spans.isEmpty()) {
                return null;
            }
            return spans.peek().getData();
        }

        public boolean isEmpty() {
            return spans.isEmpty();
        }
    }

    static class SpanNode {
        private Span data;
        private int nextSubSpanLevelId = 0;

        public SpanNode(Span data) {
            this.data = data;
            this.data.setLevelId(nextSubSpanLevelId);
        }

        public SpanNode(Span data, int levelId) {
            this.data = data;
            this.data.setLevelId(levelId);
        }

        public Span getData() {
            return data;
        }

        public int getNextSubSpanLevelId() {
            return nextSubSpanLevelId;
        }

        public void incrementNextSubSpanLevelId() {
            this.nextSubSpanLevelId++;
        }
    }

}
