package com.ai.cloud.skywalking.context;

import java.util.Stack;

public class Context {
    private static ThreadLocal<SpanNodeStack> nodes = new ThreadLocal<SpanNodeStack>();
    private static Context context;

    private Context() {
        nodes.set(new SpanNodeStack());
    }

    public void append(Span span) {
        nodes.get().push(span);
    }

    public Span getLastSpan() {
        return nodes.get().peek();
    }

    public Span removeLastSpan() {
        return nodes.get().pop();
    }

    public static Context getOrCreate() {
        if (context == null) {
            context = new Context();
        }
        return context;
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
                spans.push(new SpanNode(span, spans.peek().getLevelId()));
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

        public int getLevelId() {
            return nextSubSpanLevelId;
        }

        public void incrementNextSubSpanLevelId() {
            this.nextSubSpanLevelId++;
        }
    }

}
