package test.org.apache.skywalking.apm.testcase.resttemplate;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import test.org.apache.skywalking.apm.testcase.entity.User;

import java.io.IOException;

/**
 * @author zhangwei
 */
@RestController
public class RestTemplateController {

    private static final String SUCCESS = "Success";

    private Logger logger = LogManager.getLogger(RestTemplateController.class);

    private static final String url = "http://localhost:8080/spring-4.3.x-scenario";

    @RequestMapping("/case/resttemplate")
    @ResponseBody
    public String restTemplate() throws IOException {
        Request request = new Request.Builder().url(url + "/case/spring3/").build();
        Response response = new OkHttpClient().newCall(request).execute();
        logger.info(response.toString());

        // Create user
        HttpEntity<User> userEntity = new HttpEntity<>(new User(1, "a"));
        new RestTemplate().postForEntity(url + "/create/", userEntity, Void.class);

        // Find User
        new RestTemplate().getForEntity(url + "/get/{id}", User.class, 1);

        //Modify user
        HttpEntity<User> updateUserEntity = new HttpEntity<>(new User(1, "b"));
        new RestTemplate().put(url + "/update/{id}", updateUserEntity, userEntity.getBody().getId(), 1);

        //Delete user
        new RestTemplate().delete(url + "/delete/{id}", 1);

        Request inheritRequest = new Request.Builder().url(url + "/inherit/child/test").build();
        response = new OkHttpClient().newCall(inheritRequest).execute();
        logger.info(response.toString());

        return SUCCESS;
    }


    @RequestMapping("/healthCheck")
    @ResponseBody
    public String healthCheck() {
        return SUCCESS;
    }


}
