# Backend Dockerfile
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app
COPY gradlew gradlew.bat gradle.properties ./
COPY gradle/ gradle/
COPY build.gradle settings.gradle ./
COPY src/ src/

RUN ./gradlew bootJar --no-daemon

# Runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
