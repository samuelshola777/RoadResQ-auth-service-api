FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN groupadd --system roadresq && useradd --system --gid roadresq roadresq && mkdir -p /app/uploads && chown -R roadresq:roadresq /app
COPY --from=build /build/target/auth-service-1.0.0.jar /app/service.jar
USER roadresq
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/service.jar"]
