package com.ai.cloud.skywalking.reciever.peresistent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OffsetFile {
    private boolean                        isComplete      = true;
    private Map<String, FileRegisterEntry> registerEntries = new HashMap<String, FileRegisterEntry>();
    private long lastModifyTime;

    public OffsetFile(String offsetFileName, List<String> bufferFileNameList) throws IOException {
        File offsetFile = new File(offsetFileName);
        if (offsetFile.exists()){
            isComplete = false;
            return;
        }

        BufferedReader reader = new BufferedReader(new FileReader(offsetFile));
        String offsetData;
        String lastModifyTimeStr = reader.readLine();
        if (lastModifyTimeStr == null || lastModifyTimeStr.length() == 0) {
            isComplete = false;
            return;
        }
        
        lastModifyTime = Long.parseLong(lastModifyTimeStr);
        while ((offsetData = reader.readLine()) != null && !"EOF".equals(offsetData)) {
            String[] ss = offsetData.split("\t");
            if (bufferFileNameList.contains(ss[0])) {
                registerEntries.put(ss[0], new FileRegisterEntry(ss[0], Integer.valueOf(ss[1]),
                        FileRegisterEntry.FileRegisterEntryStatus.UNREGISTER));
            }
        }

        if (!"EOF".equals(offsetData)) {
            // 文件不完整
            isComplete = false;
        }
    }

    public boolean compare(OffsetFile offsetFile) {
        if (isComplete && !offsetFile.isComplete) {
            if (lastModifyTime > offsetFile.lastModifyTime) {
                // 优先选择完整,并且时间是最新的
                return true;
            } else {
                return false;
            }
        } else if (isComplete && offsetFile.isComplete) {
            // 都是完整,则采用时间最新的
            if (lastModifyTime > offsetFile.lastModifyTime) {
                return true;
            } else {
                return false;
            }
        } else if (!isComplete && offsetFile.isComplete) {
            //本身没有完成,但是别人完成了,采用别人的
            return false;
        } else {
            // 自己没有完成,别人也没有完成,则比较时间
            if (lastModifyTime > offsetFile.lastModifyTime) {
                return true;
            } else {
                return false;
            }
        }
    }

    public Map<String, FileRegisterEntry> getRegisterEntries() {
        return registerEntries;
    }
}
