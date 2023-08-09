FROM eclipse-temurin:17-jdk-alpine
COPY /target/hlm-proxy-*.jar /hlm-proxy.jar
CMD java -jar /hlm-proxy.jar