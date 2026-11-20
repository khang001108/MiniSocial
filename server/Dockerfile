# Multi-architecture image: works on Raspberry Pi 5 (linux/arm64) and PCs (amd64).
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /build
COPY server/src ./src
RUN mkdir -p classes && javac -encoding UTF-8 -d classes src/minisocial/*.java
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN useradd -u 10001 -r -s /usr/sbin/nologin minisocial
COPY --from=build /build/classes /app/classes
COPY web/index.html /app/web/index.html
COPY web/admin.html web/admin.js /app/web/
COPY client/dist/MiniSocial-Responsive.jar /app/download/MiniSocial-Responsive.jar
COPY client/dist/MiniSocial-Responsive.jad /app/download/MiniSocial-Responsive.jad
USER minisocial
ENV PORT=8080
EXPOSE 8080
CMD ["java","-Xms32m","-Xmx192m","-cp","/app/classes","minisocial.Server"]
