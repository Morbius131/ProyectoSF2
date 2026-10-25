FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY registry/pom.xml registry/pom.xml
COPY gateway/pom.xml  gateway/pom.xml
RUN mvn dependency:go-offline -pl gateway -am -q
COPY gateway/src gateway/src
RUN mvn clean package -pl gateway -am -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/gateway/target/gateway-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]