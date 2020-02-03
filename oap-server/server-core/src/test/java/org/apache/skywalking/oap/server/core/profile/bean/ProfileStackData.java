package org.apache.skywalking.oap.server.core.profile.bean;

import com.google.common.base.Splitter;
import lombok.Data;
import org.apache.skywalking.oap.server.core.profile.analyze.ProfileStack;

import java.util.ArrayList;
import java.util.List;

@Data
public class ProfileStackData {

    private int limit;
    private List<String> snapshots;

    public List<ProfileStack> transform() {
        ArrayList<ProfileStack> result = new ArrayList<>(snapshots.size());

        for (int i = 0; i < snapshots.size(); i++) {
            ProfileStack stack = new ProfileStack();
            stack.setSequence(i);
            stack.setDumpTime(i * limit);
            stack.setStack(Splitter.on("-").splitToList(snapshots.get(i)));
            result.add(stack);
        }

        return result;
    }

}
