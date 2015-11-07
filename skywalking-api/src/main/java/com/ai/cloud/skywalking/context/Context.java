package com.ai.cloud.skywalking.context;

import java.util.Stack;

/**
 */
public class Context {

    private static ThreadLocal<SpanStack> spans = new ThreadLocal<SpanStack>();
    private static Context context;

    private Context() {
        spans.set(new SpanStack());
    }

    public void append(Span span) {
        spans.get().push(span);
    }

    public Span getLastSpan() {
        return spans.get().peek();
    }

    public Span removeLastSpan() {
        return spans.get().pop();
    }

    public static Context getOrCreate() {
        if (context == null) {
            context = new Context();
        }
        return context;
    }

    static class SpanStack {
        private Stack<Span> spans = new Stack<Span>();

        public Span pop() {
            return spans.pop();
        }

        public void push(Span span) {
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

}
