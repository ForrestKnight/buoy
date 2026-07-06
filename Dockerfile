FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Dependency layer: re-resolved only when the build files change
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies --quiet > /dev/null || true

COPY src src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 1001 buoy
USER buoy
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
