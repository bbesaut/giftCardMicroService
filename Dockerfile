FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar
ENV JAVA_TOOL_OPTIONS="-XX:+UseSerialGC -Xss512k -Xms256m -Xmx380m"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]