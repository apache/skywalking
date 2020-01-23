package test.org.apache.skywalking.apm.testcase.implinterface;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * @auther jialong
 */
public interface TestCaseInterface {
    @RequestMapping("/impl/requestmapping")
    String implRequestMappingAnnotationTestCase();

    @GetMapping("/impl/restmapping")
    String implRestAnnotationTestCase();
}
