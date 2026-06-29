FROM maven:3.8-eclipse-temurin-8 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:resolve
COPY src ./src
RUN mvn -DskipTests clean package

FROM eclipse-temurin:8-jre
WORKDIR /app
COPY --from=build /app/target/dependency/webapp-runner.jar .
COPY --from=build /app/target/*.war .
CMD java $JAVA_OPTS -jar webapp-runner.jar --port ${PORT:-8080} *.war
