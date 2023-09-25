package org.apache.skywalking.graal;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.receiver.envoy.als.ServiceMetaInfo;
import org.apache.skywalking.oap.server.receiver.envoy.als.mx.FieldsHelper;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

@Mojo(name = "envoy-generate", defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class EnvoyGeneratorMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    @Override
    public void execute() {
        String targetDirectory = project.getBuild().getDirectory();
        try {
            generateForEnvoyMetric(getConfigFilePath(targetDirectory));
        } catch (ModuleStartException e) {
            throw new RuntimeException(e);
        }
    }

    private static void generateForEnvoyMetric(String rootPath) throws ModuleStartException {
        try {
            FieldsHelper.SINGLETON.init(Files.newInputStream(Paths.get(rootPath + "metadata-service-mapping.yaml")), ServiceMetaInfo.class);
        } catch (Exception e) {
            throw new ModuleStartException("Failed to load metadata-service-mapping.yaml", e);
        }
    }

    private static String getConfigFilePath(String targetDirectory) {
        return targetDirectory + File.separator + ".." + File.separator +
                ".." + File.separator + ".." + File.separator + ".." + File.separator +
                "oap-server" + File.separator + "server-starter" + File.separator +
                "target" + File.separator + "classes" + File.separator;
    }

}
