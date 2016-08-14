package test.json;

import com.a.eye.skywalking.web.dto.AnlyResult;
import com.a.eye.skywalking.web.entity.BreviaryChainTree;
import com.a.eye.skywalking.web.util.Constants;
import com.alibaba.fastjson.JSONObject;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xin on 16-4-18.
 */
public class TestJSON {

    public static void main(String[] args) {
        List<BreviaryChainTree> chainTreeList = new ArrayList<BreviaryChainTree>();
        BreviaryChainTree chainTree = new BreviaryChainTree("Test");
        chainTree.setEntranceViewpoint("testPoint");
        AnlyResult anlyResult = new AnlyResult();
        anlyResult.setCorrectNumber(10);
        anlyResult.setHumanInterruptionNumber(0);
        anlyResult.setTotalCall(20);
        anlyResult.setTotalCostTime(1000);
        chainTree.setEntranceAnlyResult(anlyResult);
        chainTreeList.add(chainTree);
        JSONObject jsonObject = new JSONObject();

        JsonObject result = new JsonObject();
        if (chainTreeList.size() > Constants.MAX_ANALYSIS_RESULT_PAGE_SIZE) {
            result.addProperty("hasNextPage", true);
            chainTreeList.remove(chainTreeList.size() - 1);
        } else {
            result.addProperty("hasNexPage", false);
        }
        JsonElement jsonElements =  new JsonParser().parse(new Gson().toJson(chainTreeList));
        result.add("children", jsonElements);
        jsonObject.put("code", "200");
        jsonObject.put("result", result.toString());

        System.out.println(jsonObject.toString());
    }
}
