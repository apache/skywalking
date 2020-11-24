package org.apache.skywalking.apm.testcase.cxf.service;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import org.apache.skywalking.apm.testcase.cxf.entity.User;

@WebService
public interface UserService {

	@WebMethod
	String getName(@WebParam(name = "userId") Long userId);

	@WebMethod
	User getUser(Long userId);
}
