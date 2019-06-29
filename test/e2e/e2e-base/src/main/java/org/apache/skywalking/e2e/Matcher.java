package org.apache.skywalking.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.AbstractConstruct;
import org.yaml.snakeyaml.constructor.Construct;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeId;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;

/**
 * @author kezhenxu94
 */
public class Matcher {
    //    GE("ge");
    private String value;

    Matcher(final String raw) {
        this.value = raw;
    }

    @Override
    public String toString() {
        return "Matcher{" +
            "value='" + value + '\'' +
            '}';
    }

    public static class Trace {
        private Matcher key;

        public Matcher getKey() {
            return key;
        }

        public void setKey(Matcher key) {
            this.key = key;
        }

        @Override
        public String toString() {
            return "Trace{" +
                "key=" + key +
                '}';
        }
    }

    public static class MatcherType extends AbstractConstruct {
        @Override
        public Object construct(Node node) {
            System.out.println("node = " + node);
            return new Matcher("kk");
        }
    }

    public static class TraceCtor extends Constructor {
        public TraceCtor() {
            super(Trace.class);
        }
    }

    public static void main(String[] args) {
        ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());

        Constructor constructor = new TraceCtor();
        constructor.addTypeDescription(new TypeDescription(MatcherType.class, Matcher.class));
        Yaml yaml = new Yaml(constructor);
        Trace bean = yaml.load("key: ge 1");
        System.out.println("bean = " + bean);
    }
}
