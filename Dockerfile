FROM openjdk:8
COPY /target/hlm-proxy-*.jar /hlm-proxy.jar
RUN mkdir /var/log/dockerlogs \
    && touch /var/log/dockerlogs/outlog.log
CMD java -jar /hlm-proxy.jar