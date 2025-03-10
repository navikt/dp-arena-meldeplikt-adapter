FROM gcr.io/distroless/java21

COPY build/libs/dp-arena-meldeplikt-adapter-all.jar /app.jar
CMD ["/app.jar"]
