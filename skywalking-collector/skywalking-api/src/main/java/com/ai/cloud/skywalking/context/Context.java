package com.ai.cloud.skywalking.context;

import com.ai.cloud.skywalking.protocol.Span;

import java.util.ArrayList;
import java.util.List;

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
        if (nodes.get() == null) {
            return null;
        }
        return nodes.get().pop();
    }

    public static void invalidateAllSpan() {
        if (nodes.get() == null) {
            nodes.set(new SpanNodeStack());
        }

        nodes.get().invalidateAllCurrentSpan();
    }

    static class SpanNodeStack {
        private List<SpanNode> spans = new ArrayList<SpanNode>();

        public Span pop() {
            Span span = listPop();
            if (!isEmpty()) {
                listPeek().incrementNextSubSpanLevelId();
            }
            return span;
        }

        public void push(Span span) {
            if (!isEmpty()) {
                listPush(new SpanNode(span, listPeek().getNextSubSpanLevelId()));
            } else {
                listPush(new SpanNode(span));
            }

        }

        public Span peek() {
            if (spans.isEmpty()) {
                return null;
            }
            return listPeek().getData();
        }

        public boolean isEmpty() {
            return spans.isEmpty();
        }

        private Span listPop() {
            return spans.remove(spans.size() - 1).getData();
        }

        private SpanNode listPeek() {
            return spans.get(spans.size() - 1);
        }

        private void listPush(SpanNode spanNode) {
            spans.add(spans.size(), spanNode);
        }

        public void invalidateAllCurrentSpan() {
            for (SpanNode spanNode : spans) {
                spanNode.getData().setIsInvalidate(true);
            }
        }
    }

    static class SpanNode {
        private Span data;
        //
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
