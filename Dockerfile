# Scripty — Spring Boot 3.4 / Java 17 image for Cloudflare Containers
# (and any other Docker hosts). Railway continues to use Railpack via railway.json.

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/target/scripty.jar ./scripty.jar
RUN mkdir -p /app/uploads
ENV PORT=8080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0" \
    SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
CMD ["sh", "-c", "java $JAVA_OPTS -jar scripty.jar --server.port=${PORT:-8080} --spring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod}"]
