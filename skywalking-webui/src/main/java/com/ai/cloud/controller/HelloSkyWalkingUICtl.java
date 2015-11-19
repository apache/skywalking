/**
 * 
 */
package com.ai.cloud.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.ai.cloud.service.inter.IQueryTraceLogSer;
import com.ai.cloud.util.Constants;
import com.ai.cloud.vo.mvo.BuriedPointEntry;

import freemarker.template.SimpleSequence;

/**
 * 首面请求处理
 * 
 * @author tz
 * @date 2015年11月10日 下午2:41:30
 * @version V0.3
 */
@Controller
public class HelloSkyWalkingUICtl {

	@Autowired
	IQueryTraceLogSer traceLogSer;

	private static Logger logger = LogManager.getLogger(HelloSkyWalkingUICtl.class);

	/***
	 * 默认首页
	 * 
	 * @param root
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "")
	public String showDefaultIndexPage(ModelMap root) throws Exception {
		showIndexPage(root, null);
		return "index";
	}

	/***
	 * 默认首页
	 * 
	 * @param root
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/index")
	public String showIndexPage(ModelMap root) throws Exception {
		showIndexPage(root, null);
		return "index";
	}

	/***
	 * 处理直接查看指定traceId首页
	 * 
	 * @param root
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/{traceId}")
	public String showIndexPageWithTraceId(ModelMap root, @PathVariable("traceId") String traceId) throws Exception {
		showIndexPage(root, null);
		root.put("traceId", traceId);
		return "index";
	}

	private void showIndexPage(ModelMap root, String nullStr) {
		root.put(Constants.VERSION_STR, Constants.VERSION_VAL);
	}

	/***
	 * 调度链路日志页面
	 * 
	 * @param root
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/showTraceLog/{traceId}")
	public String showTraceLog(ModelMap root, @PathVariable("traceId") String traceId) throws Exception {
//		traceId = "bcb759bc12db474aa54bc4bea605cb81123";
		Map<String, BuriedPointEntry> traceLogMap = traceLogSer.queryLogByTraceId(traceId);

		if (traceLogMap != null && traceLogMap.size() > 0) {
			List<BuriedPointEntry> valueList = new ArrayList<BuriedPointEntry>();
			valueList.addAll(traceLogMap.values());
			final List<Long> endTime = new ArrayList<Long>();
			endTime.add(0, 0l);
			Collections.sort(valueList, new Comparator<BuriedPointEntry>() {
				@Override
				public int compare(BuriedPointEntry arg0, BuriedPointEntry arg1) {
					/** 顺道取出日志最大的结束时间 */
					if (endTime.get(0) < arg0.getEndDate()) {
						endTime.set(0, arg0.getEndDate());
					}
					if (endTime.get(0) < arg1.getEndDate()) {
						endTime.set(0, arg1.getEndDate());
					}
					return arg0.getColId().compareTo(arg1.getColId());
				}
			});
			// int m = 1;
			// for (BuriedPointEntry tmpEntry : valueList) {
			// logger.info("sort result level:{} : {}", m++, tmpEntry);
			// }
			long beginTime = valueList.get(0).getStartDate();
			root.put("valueList", valueList);
			root.put("spanTypeMap", Constants.SPAN_TYPE_MAP);
			root.put("statusCodeMap", Constants.STATUS_CODE_MAP);
			root.put("beginTime", beginTime);
			root.put("endTime", endTime.get(0));
		}
		return "traceLog";
	}

	/***
	 * 登录页面
	 * 
	 * @param root
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/login")
	public String login(ModelMap root) throws Exception {
		return "login";
	}

	/***
	 * 退出
	 * 
	 * @param root
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/logout")
	public String logout(ModelMap root) throws Exception {
		return "traceLog";
	}

	/***
	 * 404错误日志
	 * 
	 * @param root
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/404")
	public String show404Page(ModelMap root) throws Exception {
		return "404";
	}

	/***
	 * 500错误日志
	 * 
	 * @param root
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/500")
	public String show500Page(ModelMap root) throws Exception {
		return "500";
	}
}
