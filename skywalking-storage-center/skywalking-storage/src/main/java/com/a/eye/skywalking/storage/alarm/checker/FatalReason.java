package com.a.eye.skywalking.storage.alarm.checker;

/**
 * Created by xin on 2016/12/8.
 */
public enum FatalReason {
   EXCEPTION_ERROR(""), EXECUTE_TIME_ERROR("-ExecuteTime-PossibleError"), EXECUTE_TIME_WARNING("-ExecuteTime-Warning");
   private String detail;

   FatalReason(String detail) {
      this.detail = detail;
   }

   public String getDetail() {
      return detail;
   }
}
