package com.a.eye.skywalking.logging;

import com.a.eye.skywalking.conf.Config;

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
