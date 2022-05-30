FROM navikt/java:17-appdynamics

COPY build/libs/eessi-fagmodul.jar /app/app.jar

ENV APPD_NAME eessi-pensjon
ENV APPD_TIER fagmodul
ENV APPD_ENABLED true
