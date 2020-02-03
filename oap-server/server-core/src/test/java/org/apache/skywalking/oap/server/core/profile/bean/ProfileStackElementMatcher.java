package org.apache.skywalking.oap.server.core.profile.bean;

import lombok.Data;
import org.apache.skywalking.oap.server.core.query.entity.ProfileStackElement;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.junit.Assert;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

@Data
public class ProfileStackElementMatcher {

    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)\\:(\\d+)");

    private String code;
    private String duration;
    private int count;
    private List<ProfileStackElementMatcher> children;

    public void verify(ProfileStackElement element) {
        // analyze duration
        Matcher durationInfo = DURATION_PATTERN.matcher(duration);
        Assert.assertTrue("duration field pattern not match", durationInfo.find());
        int duration = Integer.parseInt(durationInfo.group(1));
        int durationExcludeChild = Integer.parseInt(durationInfo.group(2));

        // assert
        assertEquals(code, element.getCodeSignature());
        assertEquals(duration, element.getDuration());
        assertEquals(durationExcludeChild, element.getDurationChildExcluded());
        assertEquals(count, element.getCount());

        if (CollectionUtils.isEmpty(children)) {
            children = Collections.emptyList();
        }
        if (CollectionUtils.isEmpty(element.getChilds())) {
            element.setChilds(Collections.emptyList());
        }
        assertEquals(children.size(), element.getChilds().size());
        for (int i = 0; i < children.size(); i++) {
            children.get(i).verify(element.getChilds().get(i));
        }

    }

}
