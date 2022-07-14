package org.apache.skywalking.oap.server.receiver.golang.module;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;

public class GolangModule extends ModuleDefine {
    public GolangModule() {
        super("receiver-golang");
    }

    @Override
    public Class[] services() {
        return new Class[0];
    }
}
