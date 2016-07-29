package com.ai.cloud.skywalking.logging;

import com.ai.cloud.skywalking.conf.Config;

public class WriterFactory {
    private WriterFactory(){
    }

    public static IWriter getLogWriter(){
        if (Config.SkyWalking.IS_PREMAIN_MODE){
            return SyncFileWriter.instance();
        }else{
            return new STDOutWriter();
        }

    }
}
