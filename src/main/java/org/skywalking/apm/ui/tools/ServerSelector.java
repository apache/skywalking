package org.skywalking.apm.ui.tools;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * @author pengys5
 */
@Component
public class ServerSelector {

    private int index = 0;

    public String select(List<String> serverList) {
        String server = null;
        int tryCnt = 0;
        do {
            int size = serverList.size();
            int selectIndex = Math.abs(index) % size;
            index ++;
            try {
                server = serverList.get(selectIndex);
            } catch (Exception e) {
            }
            if (null == server) {
                tryCnt++;
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                }
            } else {
                return server;
            }
        } while (tryCnt < 3);
        throw new RuntimeException("select server fail.");
    }
}
