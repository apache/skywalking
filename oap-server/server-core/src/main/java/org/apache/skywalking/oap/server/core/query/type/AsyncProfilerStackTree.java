package org.apache.skywalking.oap.server.core.query.type;

import com.google.common.collect.Lists;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.skywalking.oap.server.library.jfr.parser.convert.FrameTree;
import org.apache.skywalking.oap.server.library.jfr.parser.type.event.JFREventType;

import java.util.List;
import java.util.Objects;

@Setter
@Getter
@NoArgsConstructor
public class AsyncProfilerStackTree {
    private JFREventType type;
    private List<AsyncProfilerStackElement> elements;

    private int idGen = 0;

    public AsyncProfilerStackTree(JFREventType type, FrameTree tree) {
        this.type = type;
        this.elements = convertTree(-1, tree);
    }

    private List<AsyncProfilerStackElement> convertTree(int parentId, FrameTree tree) {
        AsyncProfilerStackElement asyncProfilerStackElement = new AsyncProfilerStackElement();
        asyncProfilerStackElement.setId(idGen++);
        asyncProfilerStackElement.setParentId(parentId);
        asyncProfilerStackElement.setCodeSignature(tree.getFrame());
        asyncProfilerStackElement.setTotal(tree.getTotal());
        asyncProfilerStackElement.setSelf(tree.getSelf());

        List<FrameTree> children = tree.getChildren();
        List<AsyncProfilerStackElement> result = Lists.newArrayList(asyncProfilerStackElement);
        if (Objects.isNull(children) || children.isEmpty()) {
            return result;
        }

        for (FrameTree child : children) {
            List<AsyncProfilerStackElement> childElements = convertTree(asyncProfilerStackElement.getId(), child);
            result.addAll(childElements);
        }

        return result;
    }
}
