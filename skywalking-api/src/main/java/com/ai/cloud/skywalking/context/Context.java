package com.ai.cloud.skywalking.context;

import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;

/**
 */
public class Context {

    //private static ThreadLocal<SpanStack> nodes = new ThreadLocal<SpanStack>();
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

    static class SpanStack {
        private Stack<Span> spans = new Stack<Span>();
        private AtomicInteger levelId = new AtomicInteger();

        public Span pop() {
            return spans.pop();
        }

        public void push(Span span) {
            span.setLevelId(levelId.getAndDecrement());
            spans.push(span);
        }

        public int size() {
            return spans.size();
        }

        public Span peek() {
            if (spans.isEmpty()) {
                return null;
            }
            return spans.peek();
        }

        public boolean isEmpty() {
            return spans.isEmpty();
        }
    }

    static class SpanNodeStack {
        private Stack<SpanNode> spans = new Stack<SpanNode>();

        public Span pop() {
            Span span = spans.pop().getData();
            if (!isEmpty()) {
                spans.peek().getLevelId().incrementAndGet();
            }
            return span;
        }

        public void push(Span span) {
            if (!isEmpty()) {
                spans.push(new SpanNode(span, spans.peek().getLevelId().get()));
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
        private AtomicInteger levelId = new AtomicInteger();

        public SpanNode(Span data) {
            this.data = data;
            this.data.setLevelId(levelId.get());
        }

        public SpanNode(Span data, int levelId) {
            this.data = data;
            this.data.setLevelId(levelId);
        }

        public Span getData() {
            return data;
        }

        public void setData(Span data) {
            this.data = data;
        }

        public AtomicInteger getLevelId() {
            return levelId;
        }

        public void setLevelId(AtomicInteger levelId) {
            this.levelId = levelId;
        }
    }

}
