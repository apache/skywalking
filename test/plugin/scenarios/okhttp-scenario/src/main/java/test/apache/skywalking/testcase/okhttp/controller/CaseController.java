package test.apache.skywalking.testcase.okhttp.controller;

import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/case")
public class CaseController {

    @RequestMapping("/receiveContext-1")
    @ResponseBody
    public String receiveContextService1() throws InterruptedException {
        Thread.sleep(2 * 1000);
        return "receiveContext-1";
    }

    @RequestMapping("/receiveContext-0")
    @ResponseBody
    public String receiveContextService0() throws InterruptedException {
        Thread.sleep(2 * 1000);
        return "receiveContext-0";
    }

    @RequestMapping("/okhttp-case")
    @ResponseBody
    public String okHttpScenario() {
        Request request = new Request.Builder()
            .url("http://127.0.0.1:8080/okhttp-case/case/receiveContext-0")
            .build();

        new OkHttpClient().newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                //Never do this
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Request request = new Request.Builder()
                    .url("http://127.0.0.1:8080/okhttp-case/case/receiveContext-1")
                    .build();
                new OkHttpClient().newCall(request).execute();
            }
        });

        return "Success";
    }

    @RequestMapping(value = "/healthCheck")
    @ResponseBody
    public String healthCheck() {
        return "Success";
    }
}
