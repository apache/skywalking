package org.apache.skywalking.oap.server.core.profile.bean;

import lombok.Data;
import org.apache.skywalking.oap.server.core.profile.analyze.ProfileAnalyzer;
import org.apache.skywalking.oap.server.core.profile.analyze.ProfileStack;
import org.apache.skywalking.oap.server.core.query.entity.ProfileAnalyzation;

import java.util.List;

import static org.junit.Assert.assertEquals;

@Data
public class ProfileStackAnalyze {

    private ProfileStackData data;
    private List<ProfileStackElementMatcher> except;

    public void analyzeAndAssert() {
        List<ProfileStack> stacks = data.transform();
        ProfileAnalyzation analyze = ProfileAnalyzer.analyze(stacks);

        assertEquals(analyze.getStack().size(), except.size());
        for (int i = 0; i < analyze.getStack().size(); i++) {
            except.get(i).verify(analyze.getStack().get(i));
        }
    }

}
