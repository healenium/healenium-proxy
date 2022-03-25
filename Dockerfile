#Create docker image - healenium/hlm-proxy:0.2.4

#FROM openjdk:8
#COPY /target/hlm-proxy-*.jar /hlm-proxy.jar
#RUN mkdir /var/log/dockerlogs \
#    && touch /var/log/dockerlogs/outlog.log
#CMD java -jar /hlm-proxy.jar

#docker build -t healenium/hlm-proxy:0.2.4 .

#--------------------------------------------------------------------------------------------------

#Create docker image - healenium/hlm-selenium-4-standalone-tigervnc:0.1.2

FROM healenium/base-image-bionic:1.0.0
LABEL MAINTAINER="Healenium"
RUN apt-get update \
    && apt-get install sakura \
    && apt-get install gnome-terminal
COPY ./src/main/resources/chromedriver /usr/local/bin/
COPY ./src/main/resources/geckodriver /usr/bin/
COPY ./src/main/resources/msedgedriver /usr/bin/
COPY ./src/main/resources/selenium-server-4.1.0.jar /selenium-server-4.1.0.jar
COPY ./src/main/resources/novncstart.sh /
CMD tigervncserver -xstartup /novncstart.sh -geometry 2000x1100 -depth 24 -SecurityTypes None \
    && /noVNC-1.1.0/utils/launch.sh --vnc localhost:5901