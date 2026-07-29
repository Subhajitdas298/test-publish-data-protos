# Expects the boot jar to already be built (see .github/workflows/deploy.yml or run
# `./gradlew bootJar` locally) — this keeps the GitHub Packages credentials needed to
# resolve the test-data-protos dependency out of the image build entirely.
FROM eclipse-temurin:24-jre

WORKDIR /app
COPY build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
