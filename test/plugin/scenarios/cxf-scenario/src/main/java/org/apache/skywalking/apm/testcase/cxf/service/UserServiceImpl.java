package org.apache.skywalking.apm.testcase.cxf.service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.jws.WebService;
import org.apache.skywalking.apm.testcase.cxf.entity.User;

@WebService
public class UserServiceImpl implements UserService {
    private Map<Long, User> userMap = new HashMap<Long, User>();

    public UserServiceImpl() {
        User user = new User();
        user.setUserId(1L);
        user.setUsername("skywalking");
        user.setGmtCreate(new Date());
        userMap.put(user.getUserId(), user);
    }

    @Override
    public String getName(Long userId) {
        return "hello" + userId;
    }

    @Override
    public User getUser(Long userId) {
        return userMap.get(userId);
    }

}
