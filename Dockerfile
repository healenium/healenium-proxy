FROM eclipse-temurin:22-alpine
COPY /target/hlm-proxy-*.jar /hlm-proxy.jar
CMD java -jar /hlm-proxy.jar 
