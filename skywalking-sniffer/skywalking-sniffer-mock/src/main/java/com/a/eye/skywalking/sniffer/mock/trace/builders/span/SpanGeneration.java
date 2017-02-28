package com.a.eye.skywalking.sniffer.mock.trace.builders.span;

/**
 * Created by wusheng on 2017/2/28.
 */
public abstract class SpanGeneration {
    private SpanGeneration next;

    public SpanGeneration build(SpanGeneration next){
        this.next = next;
        return next;
    }

    public SpanGeneration build(){
        return this;
    }

    protected abstract void before();

    protected abstract void after();

    public void generate(){
        this.before();
        if(next != null){
            next.generate();
        }
        this.after();
    }
}
