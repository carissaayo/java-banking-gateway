FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY src src
RUN chmod +x mvnw && ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-noble
WORKDIR /app
RUN useradd --system --home /app --shell /usr/sbin/nologin gateway \
	&& apt-get update \
	&& apt-get install -y --no-install-recommends curl \
	&& rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/target/banking-api-gateway-0.0.1-SNAPSHOT.jar app.jar
USER gateway
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=12 \
	CMD curl -fsS http://127.0.0.1:8080/actuator/health > /dev/null
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
