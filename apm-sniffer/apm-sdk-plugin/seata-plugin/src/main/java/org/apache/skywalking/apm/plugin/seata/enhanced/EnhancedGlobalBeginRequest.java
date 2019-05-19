package org.apache.skywalking.apm.plugin.seata.enhanced;

import io.seata.core.protocol.transaction.GlobalBeginRequest;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class EnhancedGlobalBeginRequest extends GlobalBeginRequest implements EnhancedRequest {
  private Map<String, String> headers = new HashMap<String, String>();

  public EnhancedGlobalBeginRequest(final GlobalBeginRequest globalBeginRequest) {
    setTimeout(globalBeginRequest.getTimeout());
    setTransactionName(globalBeginRequest.getTransactionName());
  }

  @Override
  public void put(final String key, final String value) {
    headers.put(key, value);
  }

  @Override
  public String get(final String key) {
    return headers.get(key);
  }

  public Map<String, String> getHeaders() {
    return headers;
  }

  public void setHeaders(final Map<String, String> headers) {
    this.headers = headers;
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
