# 如何追踪MySQL访问？
- 引入所需插件
```xml
<!-- jdbc插件，监控所有的jdbc调用 -->
<dependency>
    <groupId>com.ai.cloud</groupId>
    <artifactId>skywalking-jdbc-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```
- 设置特定的JDBC Driver
```properties
Driver="com.ai.cloud.skywalking.plugin.jdbc.mysql.MySQLTracingDriver"
```
- 设置特定的JDBC URL
```properties
jdbc.url=tracing:jdbc:mysql://localhost:3306/test?useUnicode=true&characterEncoding=UTF-8
```

# 如何追踪MySQL之外的其他JDBC？ 
- 引入所需插件
```xml
<!-- jdbc插件，监控所有的jdbc调用 -->
<dependency>
    <groupId>com.ai.cloud</groupId>
    <artifactId>skywalking-jdbc-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```
- 轻松实现自定义的JDBC Driver扩展
```java
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;

import com.ai.cloud.skywalking.plugin.jdbc.TracingDriver;

public class XXXDBTracingDriver extends TracingDriver {
	static {
		try {
			DriverManager.registerDriver(new XXXDBTracingDriver());
		} catch (SQLException e) {
			throw new RuntimeException("register "
					+ MySQLTracingDriver.class.getName() + " driver failure.");
		}
	}

	/**
	 * 继承自TracingDriver，返回真实的Driver
	 */
	@Override
	protected Driver registerTracingDriver() {
		try {
			//示例：return new com.mysql.jdbc.Driver();
			return new Driver();
		} catch (SQLException e) {
			throw new RuntimeException("create Driver failure.");
		}
	}
}
```
- 设置新实现的JDBC Driver
```properties
Driver="XXXDBTracingDriver"
```
- 设置特定的JDBC URL
```properties
jdbc.url=tracing:jdbc:xxxdb://localhost:3306/test
```