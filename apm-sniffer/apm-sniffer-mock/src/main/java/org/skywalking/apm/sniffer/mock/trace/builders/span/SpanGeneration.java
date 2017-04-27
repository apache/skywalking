package org.skywalking.apm.sniffer.mock.trace.builders.span;

/**
 * The <code>SpanGeneration</code> implementations can generate several kinds of spans.
 *
 * @author wusheng
 */
public abstract class SpanGeneration {
    private SpanGeneration[] next;

    public SpanGeneration build(SpanGeneration next) {
        this.next = new SpanGeneration[] {next};
        return next;
    }

    public void build(SpanGeneration... next) {
        this.next = next;
    }

    protected abstract void before();

    protected abstract void after();

    public void generate() {
        this.before();
        if (next != null) {
            for (SpanGeneration generation : next) {
                generation.generate();
            }
        }
        this.after();
    }
}
