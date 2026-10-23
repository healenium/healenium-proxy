FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/hlm-proxy-*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
