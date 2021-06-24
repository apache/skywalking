package org.apache.skywalking.oap.server.core.storage.jmh;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

@RequiredArgsConstructor
public class BlockingBatchQueueWithSynchronized<E> implements BlockingBatchQueue<E> {

    @Getter
    private final int maxBatchSize;

    @Getter
    private boolean inAppendingMode = true;

    private final List<E> elementData = new ArrayList<>(50000);

    public void putMany(List<E> elements) {
        synchronized (elementData) {
            elementData.addAll(elements);
            if (elementData.size() >= maxBatchSize) {
                elementData.notifyAll();
            }
        }
    }

    public List<E> popMany() throws InterruptedException {
        synchronized (elementData) {
            while (this.elementData.size() < maxBatchSize && inAppendingMode) {
                elementData.wait(1000);
            }
            if (CollectionUtils.isEmpty(elementData)) {
                return Collections.EMPTY_LIST;
            }
            List<E> sublist = this.elementData.subList(
                0, Math.min(maxBatchSize, this.elementData.size()));
            List<E> partition = new ArrayList<>(sublist);
            sublist.clear();
            return partition;
        }
    }

    public void noFurtherAppending() {
        synchronized (elementData) {
            inAppendingMode = false;
            elementData.notifyAll();
        }
    }

    public void furtherAppending() {
        synchronized (elementData) {
            inAppendingMode = true;
            elementData.notifyAll();
        }
    }

    @Override
    public int size() {
        return elementData.size();
    }
}
