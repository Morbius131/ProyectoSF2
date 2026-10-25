FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY registry/pom.xml registry/pom.xml
COPY gateway/pom.xml  gateway/pom.xml
RUN mvn dependency:go-offline -pl registry -am -q
COPY registry/src registry/src
RUN mvn clean package -pl registry -am -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/registry/target/registry-1.0.0.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]