# Stage 1: Build the application
FROM gradle:8-jdk17 AS build
COPY --chown=gradle:gradle src/main/java/org/example/forthewater/service /home/gradle/src
WORKDIR /home/gradle/src
# Build the jar skipping tests for speed
RUN gradle build -x test --no-daemon

# Stage 2: Run the application
FROM eclipse-temurin:17-jre-alpine
EXPOSE 8080
COPY --from=build /home/gradle/src/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]