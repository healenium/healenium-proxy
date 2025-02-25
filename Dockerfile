FROM eclipse-temurin:23-alpine
COPY /target/hlm-proxy-*.jar /hlm-proxy.jar
CMD java -jar /hlm-proxy.jar
