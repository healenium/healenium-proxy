FROM openjdk:8-jre-alpine
COPY /target/hlm-proxy-*.jar /hlm-proxy.jar
CMD java -jar /hlm-proxy.jar