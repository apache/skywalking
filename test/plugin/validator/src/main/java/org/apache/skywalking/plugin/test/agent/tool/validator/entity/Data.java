package org.apache.skywalking.plugin.test.agent.tool.validator.entity;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import org.apache.skywalking.plugin.test.agent.tool.validator.exception.IllegalDataFileException;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;

public interface Data {
    RegistryItems registryItems();

    List<SegmentItem> segmentItems();

    class Loader {
        public static Data loadData(String fileName, File file) {
            try {
                return loadData(new FileInputStream(file));
            } catch (Exception e) {
                throw new IllegalDataFileException(fileName);
            }
        }

        public static Data loadData(InputStream inputStream) {
            Constructor constructor = new Constructor(DataForRead.class);
            TypeDescription configDescription = new TypeDescription(DataForRead.class);
            configDescription.putListPropertyType("data", DataForRead.class);

            Representer representer = new Representer();
            representer.getPropertyUtils().setSkipMissingProperties(true);
            Yaml yaml = new Yaml(constructor, representer);
            Data result = yaml.loadAs(inputStream, DataForRead.class);
            if (result == null) {
                throw new RuntimeException();
            } else{
                return result;
            }
        }
    }
}
