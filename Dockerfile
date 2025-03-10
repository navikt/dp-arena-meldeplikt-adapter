FROM gcr.io/distroless/java21-debian12:nonroot

COPY build/libs/dp-arena-meldeplikt-adapter-all.jar /app/app.jar
EXPOSE 8080
