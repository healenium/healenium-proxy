#Create docker image - healenium/hlm-proxy:0.2.4.1-selenium-4

FROM openjdk:8
COPY /target/hlm-proxy-*.jar /hlm-proxy.jar
RUN mkdir /var/log/dockerlogs \
    && touch /var/log/dockerlogs/outlog.log
CMD java -jar /hlm-proxy.jar > /var/log/dockerlogs/outlog.log

#docker build -t healenium/hlm-proxy:0.2.4.1-selenium-4 .