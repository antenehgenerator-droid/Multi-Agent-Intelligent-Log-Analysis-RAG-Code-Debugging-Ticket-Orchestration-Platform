FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
ARG JAR_FILE=target/service-embedding-worker-*.jar
COPY ${JAR_FILE} app.jar
EXPOSE 8060
ENTRYPOINT ["java", "-jar", "app.jar"]
