FROM gradle:8.14.0-jdk17 AS build
WORKDIR /home/gradle/project
COPY . .
RUN gradle clean bootJar -x test --no-daemon

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /home/gradle/project/build/libs/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

