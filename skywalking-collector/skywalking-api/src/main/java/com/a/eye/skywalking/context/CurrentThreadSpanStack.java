package com.a.eye.skywalking.context;

import com.a.eye.skywalking.protocol.Span;

import java.util.ArrayList;
import java.util.List;

public class CurrentThreadSpanStack {
    private static ThreadLocal<SpanNodeStack> nodes = new ThreadLocal<SpanNodeStack>();

    private CurrentThreadSpanStack() {

    }

    public static void push(Span span) {
        if (nodes.get() == null) {
            nodes.set(new SpanNodeStack());
        }
        nodes.get().push(span);
    }

    public static Span peek() {
        if (nodes.get() == null) {
            return null;
        }
        return nodes.get().peek();
    }

    public static Span pop() {
        if (nodes.get() == null) {
            return null;
        }
        return nodes.get().pop();
    }

    static class SpanNodeStack {
        /**
         * 单JVM的单线程,埋点数量一般不会超过20.
         * 超过20会影响性能,不推荐使用
         */
        private List<SpanNode> spans = new ArrayList<SpanNode>(20);

        public Span pop() {
            Span span = spans.remove(getTopElementIdx()).getData();
            if (!isEmpty()) {
                spans.get(getTopElementIdx()).incrementNextSubSpanLevelId();
            }
            return span;
        }

        public void push(Span span) {
            if (!isEmpty()) {
                listPush(new SpanNode(span, spans.get(getTopElementIdx()).getNextSubSpanLevelId()));
            } else {
                listPush(new SpanNode(span));
            }

        }

        public Span peek() {
            if (spans.isEmpty()) {
                return null;
            }
            return spans.get(getTopElementIdx()).getData();
        }

        private int getTopElementIdx() {
            return spans.size() - 1;
        }

        private boolean isEmpty() {
            return spans.isEmpty();
        }

        private void listPush(SpanNode spanNode) {
            spans.add(spans.size(), spanNode);
        }

    }

    static class SpanNode {
        private Span data;

        private int nextSubSpanLevelId = 0;

        public SpanNode(Span data) {
            this.data = data;
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
