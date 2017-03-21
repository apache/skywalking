package com.a.eye.skywalking.ui.tools;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author pengys5
 */
@Component
public class ServerSelector {

    private int index = 0;

    public String select(List<String> serverList) {
        int size = serverList.size();
        index++;
        int selectIndex = Math.abs(index) % size;
        return serverList.get(selectIndex);
    }
}
