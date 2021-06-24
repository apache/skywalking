package org.apache.skywalking.oap.server.core.storage.jmh;

import java.util.List;

public interface BlockingBatchQueue<E> {
    public List<E> popMany() throws InterruptedException;

    public void putMany(List<E> elements);

    public void noFurtherAppending();

    public void furtherAppending();

    int size();
}
