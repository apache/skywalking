package test.com.ai.skywalking.plugin.tomcat78x;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.webresources.DirResourceSet;
import org.apache.catalina.webresources.StandardRoot;
import org.junit.Test;

import javax.servlet.ServletException;
import java.io.File;

public class EmbedTomcat {

    @Test
    public void testGetResourcePath(){
        System.out.println(EmbedTomcat.class.getResource("/").getPath());
    }

    @Test
    public void testStartTomcat() throws ServletException, LifecycleException {
        start();
    }

    public static void start() throws ServletException, LifecycleException {
        String webappDirLocation = EmbedTomcat.class.getResource("/").getPath() + "webapp/";
        Tomcat tomcat = new Tomcat();
        tomcat.setPort(8080);

        StandardContext standardContext = (StandardContext) tomcat.addWebapp("/",new File(webappDirLocation).getAbsolutePath());
        File additionWebInfClasses = new File(EmbedTomcat.class.getResource("/").getPath());
        WebResourceRoot resourceRoot = new StandardRoot(standardContext);
        resourceRoot.addPreResources(new DirResourceSet(resourceRoot,"/WEB-INF/classes", additionWebInfClasses.getAbsolutePath(), "/"));
        standardContext.setResources(resourceRoot);

        tomcat.start();
    }
}
