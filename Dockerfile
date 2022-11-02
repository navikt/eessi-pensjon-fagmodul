FROM ghcr.io/navikt/baseimages/temurin:17

COPY init-scripts/ep-jvm-tuning.sh /init-scripts/

COPY build/libs/eessi-fagmodul.jar /app/app.jar
