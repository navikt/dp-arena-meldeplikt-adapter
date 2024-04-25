FROM ghcr.io/navikt/baseimages/temurin:21

COPY build/libs/dp-arena-meldeplikt-adapter-all.jar /app/app.jar
EXPOSE 8080
