FROM openjdk:8-jre-alpine
RUN apk add --update font-adobe-100dpi ttf-dejavu fontconfig
ADD simsun.ttf /usr/share/fonts/ttf-dejavu/simsun.ttf
VOLUME /tmp
ADD wyy-admin.jar wyy-admin.jar
ENTRYPOINT ["java", "-Xms200m", "-Xmx1G", "-Duser.timezone=GMT+08", "-jar", "/wyy-admin.jar"]
