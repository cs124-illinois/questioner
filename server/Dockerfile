FROM eclipse-temurin:21-jdk-jammy

ARG TARGETARCH
ENV TINI_VERSION=v0.19.0
ADD https://github.com/krallin/tini/releases/download/${TINI_VERSION}/tini-${TARGETARCH} /tini
RUN chmod 755 /tini
ENTRYPOINT ["/tini", "--"]

RUN apt update && apt install -y procps libjemalloc2 && rm -rf /var/lib/apt/lists/*
RUN cp /usr/lib/`arch`-linux-gnu/libjemalloc.so.2 /usr/lib/
ENV LD_PRELOAD=/usr/lib/libjemalloc.so.2

# Fail a task rather than silently losing capture if anything replaces the sandbox's output
# streams. ktor 3.5's CallLogging did exactly that via jansi, which cost us a debugging cycle and
# would have mis-graded every output-based question.
ENV JEED_FAIL_ON_REPLACED_STREAMS=true

WORKDIR /
COPY *.jar questioner.jar

# Student code runs inside this JVM. If it ever gets out of the sandbox, it should be nobody
# in particular, not root with root's capabilities. Numeric so Kubernetes' runAsNonRoot can verify
# it. The server writes nothing outside /tmp (the JVM's perf data, Jeed's empty jeed-cache
# directory, and heap dumps if DUMP_AFTER_HEARTBEAT is set), so the root filesystem can be mounted
# read-only.
RUN groupadd --gid 10001 questioner && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin questioner
USER 10001:10001
SHELL ["/bin/sh", "-c"]
# -XX:-UseBASE64Intrinsics mitigates CVE-2026-114938: a heap overflow in the AVX-512 Base64 decode
# intrinsic on Java 17/21/25. Student code runs in this JVM and can call java.util.Base64, so a
# crafted submission could crash the server. Remove once the base image ships a fixed Java 21 build.
CMD java -ea --enable-preview -Dfile.encoding=UTF-8 \
    -Djava.security.manager=allow -XX:-OmitStackTraceInFastThrow \
    -XX:-UseBASE64Intrinsics \
    -XX:+UnlockExperimentalVMOptions -XX:-VMContinuations \
    -Xss256k -XX:+UseZGC -XX:+ZGenerational \
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
