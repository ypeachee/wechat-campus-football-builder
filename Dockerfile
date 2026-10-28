FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl tzdata \
    && addgroup -S app \
    && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/target/*.jar /app/app.jar
USER app
EXPOSE 8080
ENV JAVA_OPTS="-Xms32m -Xmx256m -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m -XX:MaxDirectMemorySize=32m -XX:+UseSerialGC -XX:ActiveProcessorCount=2 -Xss256k -Djava.security.egd=file:/dev/urandom"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]