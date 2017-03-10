package com.a.eye.skywalking.api.logging;

import com.a.eye.skywalking.api.conf.Config;

public class WriterFactory {
    public static IWriter getLogWriter(){
        if (Config.SkyWalking.IS_PREMAIN_MODE){
            return SyncFileWriter.instance();
        }else{
            return new STDOutWriter();
        }

    }
}
