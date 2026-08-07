FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY . .

ENV GRADLE_OPTS="-Xmx512m -Dorg.gradle.daemon=false"

RUN ./gradlew clean bootJar -x test --no-daemon

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]