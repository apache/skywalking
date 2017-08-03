package org.skywalking.apm.ui.tools;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * @author pengys5
 */
@Component
public class ServerSelector {

    private final Integer MAX_INDEX = Integer.MAX_VALUE - 10000;

    private int index = 0;

    public String select(List<String> serverList) {
        int size = serverList.size();
        int selectIndex = Math.abs(index) % size;

        if (index > MAX_INDEX) {
            index = 0;
        }
        try {
            return serverList.get(selectIndex);
        } catch (Exception e) {
        }
        return serverList.get(0);
    }
}
