package org.apache.skywalking.oap.server.receiver.trace.provider;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.configuration.api.ConfigChangeWatcher;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.receiver.trace.module.TraceModule;

import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class TraceSampleRateWatcher extends ConfigChangeWatcher {
    private  AtomicReference<String> settingsString;
    private  AtomicReference<Integer> sampleRate;

    public TraceSampleRateWatcher(String config, TraceModuleProvider provider){
        super(TraceModule.NAME, provider, "sampleRate");
        settingsString = new AtomicReference<>(Const.EMPTY_STRING);
        sampleRate = new AtomicReference<>();

        activeSetting(config);
    }

    private void activeSetting(String config) {
        if (log.isDebugEnabled()) {
            log.debug("Updating using new static config: {}", config);
        }
        settingsString.set(config);
        try {
            int t_sampleRate = Integer.parseInt(config);
            sampleRate.set(t_sampleRate);
        }catch (NumberFormatException ex){
            log.error("Cannot load sampleRate from: {}", config,ex);
        }
    }

    @Override
    public void notify(ConfigChangeEvent value) {
        if (EventType.DELETE.equals(value.getEventType())) {
            activeSetting("");
        } else {
            activeSetting(value.getNewValue());
        }
    }

    @Override
    public String value() {
        return settingsString.get();
    }

    public int getSampleRate(){
        return sampleRate.get()==null?((TraceModuleProvider)this.getProvider()).getModuleConfig().getSampleRate():sampleRate.get();
    }
}
