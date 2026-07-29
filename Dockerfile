# Expects the boot jar to already be built (see .github/workflows/deploy.yml or run
# `./gradlew bootJar` locally) — this keeps the GitHub Packages credentials needed to
# resolve the test-data-protos dependency out of the image build entirely.
FROM eclipse-temurin:24-jre

WORKDIR /app
COPY build/libs/*.jar app.jar

EXPOSE 8080
# SerialGC + a higher RAM percentage suit a single-instance, scale-to-zero container
# better than the default G1/25% (small footprint, faster cold-start GC init);
# TieredStopAtLevel=1 skips C2 warmup to cut cold-start latency, an acceptable
# trade for a low-throughput demo API.
ENTRYPOINT ["java", "-XX:+UseSerialGC", "-XX:MaxRAMPercentage=75.0", "-XX:TieredStopAtLevel=1", "-jar", "/app/app.jar"]
