package org.apache.skywalking.e2e.profile.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.skywalking.e2e.verification.AbstractMatcher;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class ProfiledSpanTagMatcher extends AbstractMatcher<ProfiledSpanTag> {
    private String key;
    private String value;
    @Override
    public void verify(ProfiledSpanTag profiledSpanTag) {
        if (value == null) {
            value = "";
        }

        doVerify(key, profiledSpanTag.getKey());
        doVerify(value, profiledSpanTag.getValue());
    }
}
