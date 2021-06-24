package org.apache.skywalking.oap.server.core.storage;

import com.google.common.annotations.VisibleForTesting;
import java.util.List;

/**
 A blocking batch queue.

 Use offer a list to the Queue or blocking poll a list from Queue.
 */
interface BlockingBatchQueue<E> {
    List<E> poll() throws InterruptedException;

    void offer(List<E> elements);

    void noFurtherAppending();

    @VisibleForTesting
    void furtherAppending();

    @VisibleForTesting
    int size();
}
