package org.apache.skywalking.oap.server.core.source;

import lombok.Getter;
import lombok.Setter;

public abstract class TagAutocomplete extends Source {

    @Override
    public int scope() {
        return DefaultScopeDefine.TRACE_TAG_AUTOCOMPLETE;
    }

    @Override
    public String getEntityId() {
        return tag;
    }

    @Setter
    @Getter
    private String tag;
    @Setter
    @Getter
    private String tagKey;
    @Setter
    @Getter
    private String tagValue;

}
