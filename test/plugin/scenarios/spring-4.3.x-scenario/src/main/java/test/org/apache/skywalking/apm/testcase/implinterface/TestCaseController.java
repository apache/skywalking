package test.org.apache.skywalking.apm.testcase.implinterface;

import org.springframework.web.bind.annotation.RestController;

/**
 * @auther jialong
 */
@RestController
public class TestCaseController implements TestCaseInterface {

    @Override
    public String implRequestMappingAnnotationTestCase() {
        return "implRequestMappingAnnotationTestCase";
    }

    @Override
    public String implRestAnnotationTestCase() {
        return "implRestAnnotationTestCase";
    }
}
