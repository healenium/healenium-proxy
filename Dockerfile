FROM eclipse-temurin:23
COPY /target/hlm-proxy-*.jar /hlm-proxy.jar
CMD java -jar /hlm-proxy.jar
