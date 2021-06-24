package org.apache.skywalking.oap.server.core.storage.jmh;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class BlockingBatchQueueWithLinkedBlockingQueue<E> implements BlockingBatchQueue<E> {

    @Getter
    private final int maxBatchSize;

    @Getter
    private volatile boolean inAppendingMode = true;

    private final LinkedBlockingQueue<E> elementData = new LinkedBlockingQueue<>();

    public void putMany(List<E> elements) {
        elementData.addAll(elements);
    }

    public List<E> popMany() throws InterruptedException {
        List<E> result = new ArrayList<>();
        do {
            E take = elementData.poll(1000, TimeUnit.MILLISECONDS);
            if (take != null) {
                result.add(take);
            }
            if (result.size() >= maxBatchSize) {
                return result;
            }
            if (!inAppendingMode && this.elementData.isEmpty()) {
                return result;
            }
        }
        while (!this.elementData.isEmpty());
        return result;

    }

    public void noFurtherAppending() {
        inAppendingMode = false;
    }

    public void furtherAppending() {
        inAppendingMode = true;
    }

    @Override
    public int size() {
        return this.elementData.size();
    }
}
