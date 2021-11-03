FROM openjdk:8
COPY /target/hlm-proxy-*.jar /hlm-proxy.jar
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/hlm-proxy.jar"]