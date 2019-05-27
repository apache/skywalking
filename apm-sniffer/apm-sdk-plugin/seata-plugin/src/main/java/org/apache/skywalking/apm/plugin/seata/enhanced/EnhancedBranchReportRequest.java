package org.apache.skywalking.apm.plugin.seata.enhanced;

import io.seata.core.protocol.transaction.BranchReportRequest;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class EnhancedBranchReportRequest extends BranchReportRequest implements EnhancedRequest {
  private Map<String, String> headers = new HashMap<String, String>();

  public EnhancedBranchReportRequest(final BranchReportRequest reportRequest) {
    setApplicationData(reportRequest.getApplicationData());
    setBranchType(reportRequest.getBranchType());
    setBranchId(reportRequest.getBranchId());
    setResourceId(reportRequest.getResourceId());
    setXid(reportRequest.getXid());
    setStatus(reportRequest.getStatus());
  }

  public Map<String, String> getHeaders() {
    return headers;
  }

  public void setHeaders(final Map<String, String> headers) {
    this.headers = headers;
  }

  @Override
  public void put(final String key, final String value) {
    headers.put(key, value);
  }

  @Override
  public String get(final String key) {
    return headers.get(key);
  }

  @Override
  public byte[] encode() {
    return EnhancedRequestHelper.encode(super.encode(), getHeaders());
  }

  @Override
  public void decode(final ByteBuffer byteBuffer) {
    super.decode(byteBuffer);
    EnhancedRequestHelper.decode(byteBuffer, getHeaders());
  }
}
