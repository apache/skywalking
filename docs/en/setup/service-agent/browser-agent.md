## Browser Monitoring
[Apache SkyWalking Client JS](https://github.com/apache/skywalking-client-js) is a client-side JavaScript exception and tracing library.

It has these features:
- Provides metrics and error collection to SkyWalking backend.
- Lightweight. A simple JavaScript library. No browser plugin is required. 
- Browser serves as a starting point for the entire distributed tracing system.

See Client JS [official doc](https://github.com/apache/skywalking-client-js#quick-start) for more information.

Note: Make sure receiver-browser is enabled. It is **ON** by default since version 8.2.0.

```yaml
receiver-browser:
  selector: ${SW_RECEIVER_BROWSER:default} // This means activated.
  default:
    # The sample rate precision is 1/10000. 10000 means 100% sample in default.
    sampleRate: ${SW_RECEIVER_BROWSER_SAMPLE_RATE:10000}
```
