package org.skywalking.apm.ui.tools;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.stereotype.Component;

/**
 * @author pengys5
 */
@Component
public class ServerSelector {

    private AtomicInteger index = new AtomicInteger();
    private Integer MAX_INDEX = Integer.MAX_VALUE - 10000;
    private ReentrantLock lock = new ReentrantLock();

    public String select(List<String> serverList) {
        int size = serverList.size();
        int tmpIndex = index.incrementAndGet();
        int selectIndex = Math.abs(tmpIndex) % size;

        if (tmpIndex > MAX_INDEX) {
            try {
                lock.lock();
                if (index.get() > MAX_INDEX) {
                    index.set(0);
                }
            } finally {
                lock.unlock();
            }
        }
        return serverList.get(selectIndex);
    }
}
