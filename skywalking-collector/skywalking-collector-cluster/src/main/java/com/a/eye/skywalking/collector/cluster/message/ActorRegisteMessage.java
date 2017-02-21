package com.a.eye.skywalking.collector.cluster.message;

import java.io.Serializable;

/**
 * Created by Administrator on 2017/2/21 0021.
 */
public interface ActorRegisteMessage {

    public static class RegisteMessage implements Serializable {
        public final String role;
        public final String action;

        public RegisteMessage(String role, String action) {
            this.role = role;
            this.action = action;
        }

        public String getRole() {
            return role;
        }

        public String getAction() {
            return action;
        }
    }

    public static class RegisteMessageResult implements Serializable{
        public final String role;
        public final Integer value;

        public RegisteMessageResult(String role, Integer value){
            this.role = role;
            this.value = value;
        }

        public String getRole() {
            return role;
        }

        public Integer getValue() {
            return value;
        }
    }
}
