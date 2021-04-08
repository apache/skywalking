FROM skywalking/agent:latest

ADD test/e2e/e2e-service-provider/target/e2e-service-provider-1.0.0.jar /app.jar

CMD ["java","-jar","/app.jar"]
