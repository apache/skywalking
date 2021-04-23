# Register mechanism is no longer required for local / exit span

Since version 6.6.0, SkyWalking has removed the local and exit span registers. If an old java agent (before 6.6.0) is still running,
which registers to the 6.6.0+ backend, you will face the following warning message.
```
class=RegisterServiceHandler, message = Unexpected endpoint register, endpoint isn't detected from server side.
```

This will not harm the backend or cause any issues, but serves as a reminder that your agent or other clients should follow the new protocol
requirements.

You could simply use `log4j2.xml` to filter this warning message out.
