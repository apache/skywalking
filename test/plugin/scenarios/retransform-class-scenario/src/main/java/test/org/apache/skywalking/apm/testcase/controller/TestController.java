package test.org.apache.skywalking.apm.testcase.controller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.skywalking.apm.testcase.retransform_class.RetransformUtil;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * @author gongdewei 2020/6/7
 */
@Controller
@RequestMapping("/test")
public class TestController {

    private static final Logger logger = LogManager.getLogger(TestController.class);

    @RequestMapping("/dosomething")
    public ResponseEntity dosomething() {
        // check retransform is successful or not
        if (RetransformUtil.RETRANSFORMING_TAG.equals(RetransformUtil.RETRANSFORM_VALUE)) {
            logger.info("retransform check success.");
            return ResponseEntity.ok("retransform success");
        } else {
            logger.info("retransform check failure.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("retransform failure");
        }
    }


}
