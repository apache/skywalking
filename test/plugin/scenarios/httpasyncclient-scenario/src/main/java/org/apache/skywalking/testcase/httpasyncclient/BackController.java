package org.apache.skywalking.testcase.httpasyncclient;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/httpasyncclient")
public class BackController {
    @GetMapping("/back")
    public String back() {
        return "Hello back";
    }
}
