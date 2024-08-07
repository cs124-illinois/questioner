FROM openjdk:21-jdk-slim

ARG TARGETARCH
ENV TINI_VERSION=v0.19.0
ADD https://github.com/krallin/tini/releases/download/${TINI_VERSION}/tini-${TARGETARCH} /tini
RUN chmod +x /tini
ENTRYPOINT ["/tini", "--"]

WORKDIR /
COPY *.jar questioner.jar
SHELL ["/bin/sh", "-c"]
CMD java -ea --enable-preview -Dfile.encoding=UTF-8 -Djava.security.manager=allow \
    -Xss256k -XX:+UseZGC -XX:ZCollectionInterval=8 -XX:-OmitStackTraceInFastThrow \
    -XX:+UnlockExperimentalVMOptions -XX:-VMContinuations \
    --add-opens java.base/java.lang=ALL-UNNAMED \
    --add-opens java.base/java.util=ALL-UNNAMED \
    --add-exports jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED \
    --add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED \
    --add-exports jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED \
    --add-exports jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED \
    --add-exports jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED \
    --add-exports java.management/sun.management=ALL-UNNAMED \
    $JAVA_OPTS \
    -jar questioner.jar

# vim: tw=0
