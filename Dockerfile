FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21@sha256:8f73a60b4ca9f98e226d8750e9dbc633a1ad3833c056a5b3816804d6ec4ebae4

ENV LANG='nb_NO.UTF-8' LANGUAGE='nb_NO:nb' LC_ALL='nb:NO.UTF-8' TZ="Europe/Oslo"

COPY build/libs/dp-arena-meldeplikt-adapter-all.jar /app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
