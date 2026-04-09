# =============================================================================
# Dynamic Thread Pool Server - Production Dockerfile
# =============================================================================
#
# This Dockerfile is used by fabric8 docker-maven-plugin.
# The JAR is built locally by Maven, then assembled into the image.
#
# Build via Maven (recommended):
#   mvn clean package -pl dynamic-thread-server -am
#   => automatically builds Docker image: dynamic-thread-server:1.0.0
#
# Build via Maven (skip Docker):
#   mvn clean package -pl dynamic-thread-server -am -Ddocker.skip=true
#
# Run (standalone):
#   docker run -d -p 8080:8080 -p 9527:9527 dynamic-thread-server:latest
#
# Run (with custom JVM heap):
#   docker run -d -e JVM_XMS=4g -e JVM_XMX=4g -p 8080:8080 -p 9527:9527 dynamic-thread-server:latest
# =============================================================================

FROM docker.xuanyuan.run/eclipse-temurin:21-jre-jammy

LABEL maintainer="liuruinian <liuruinian@foxmail.com>"
LABEL description="Dynamic Thread Pool Dashboard Server"
LABEL version="1.0.0"

# --- System Setup ---
RUN groupadd -r dtpool && useradd -r -g dtpool -s /sbin/nologin dtpool

# --- Directory Structure ---
RUN mkdir -p /opt/app /opt/logs/logs /opt/logs/gc /opt/logs/heapdump \
    && chown -R dtpool:dtpool /opt/app /opt/logs

WORKDIR /opt/app

# --- Copy Artifact (assembled by fabric8 docker-maven-plugin into maven/) ---
COPY --chown=dtpool:dtpool maven/dynamic-thread-server-1.0.0.jar app.jar

# ==========================================================================
# Environment Variables - All configurable at runtime
# ==========================================================================

# --- Spring ---
ENV SPRING_PROFILES_ACTIVE="prod"
ENV SERVER_PORT="8080"

# --- JVM Memory (override for different machine sizes) ---
ENV JVM_XMS="12g"
ENV JVM_XMX="12g"
ENV JVM_MAX_DIRECT_MEMORY="460m"
ENV JVM_METASPACE="330m"
ENV JVM_MAX_METASPACE="330m"
ENV JVM_CODE_CACHE="256m"

# --- Dashboard Authentication ---
ENV DASHBOARD_USERNAME="admin"
ENV DASHBOARD_PASSWORD="admin123"

# --- Netty Server ---
ENV NETTY_PORT="9527"
ENV NETTY_WORKER_THREADS="4"
ENV NETTY_SSL_ENABLED="false"
ENV NETTY_SSL_CERT_PATH=""
ENV NETTY_SSL_KEY_PATH=""

# --- Security ---
ENV MAX_CONNECTIONS="10000"
ENV MAX_CONNECTIONS_PER_IP="100"
ENV MAX_CONNECTIONS_PER_APP="500"
ENV CONNECT_RATE_LIMIT="60"
ENV MAX_LOGIN_ATTEMPTS="5"
ENV LOGIN_LOCKOUT_MINUTES="15"
ENV AGENT_AUTH_ENABLED="false"
ENV AGENT_SECRET_KEY="dynamic-thread-default-secret-key-change-in-production"

# --- Notification Platforms ---
ENV DINGTALK_WEBHOOK=""
ENV DINGTALK_SECRET=""
ENV WECHAT_WEBHOOK=""
ENV EMAIL_SMTP_HOST=""
ENV EMAIL_SMTP_PORT=""
ENV EMAIL_USERNAME=""
ENV EMAIL_PASSWORD=""
ENV EMAIL_FROM=""
ENV EMAIL_TO=""
ENV EMAIL_SSL=""

# --- Cluster Configuration ---
ENV CLUSTER_ENABLED="false"
ENV CLUSTER_NODE_ID=""
ENV CLUSTER_NODE_ADDRESS=""
ENV CLUSTER_PORT="9528"
ENV CLUSTER_MEMBERS=""
ENV CLUSTER_SYNC_INTERVAL="3000"
ENV CLUSTER_HEARTBEAT_INTERVAL="5000"
ENV CLUSTER_NODE_TIMEOUT="15000"

# --- Logging ---
ENV LOG_LEVEL="INFO"
ENV LOG_PATH="/opt/logs"
ENV LOG_MAX_HISTORY="30"
ENV LOG_MAX_FILE_SIZE="100MB"
ENV LOG_TOTAL_SIZE_CAP="3GB"

# --- Extra JVM Options (append custom flags at runtime) ---
ENV JVM_EXTRA_OPTS=""

# ==========================================================================
# Exposed Ports
# ==========================================================================
# 8080  - HTTP API (Dashboard + Actuator)
# 9527  - Netty TCP (Agent communication)
# 9528  - Cluster TCP (Inter-node communication)
EXPOSE 8080 9527 9528

# --- Health Check ---
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -sf http://localhost:${SERVER_PORT}/actuator/health || exit 1

# --- Run as non-root ---
USER dtpool

# ==========================================================================
# Entrypoint - Production JVM Configuration
# ==========================================================================
ENTRYPOINT ["sh", "-c", "\
    exec java \
    -server \
    -Xms${JVM_XMS} \
    -Xmx${JVM_XMX} \
    -XX:+UnlockExperimentalVMOptions \
    -XX:+UnlockDiagnosticVMOptions \
    -XX:+UseZGC \
    -XX:+UseDynamicNumberOfGCThreads \
    -XX:ConcGCThreads=3 \
    -XX:ParallelGCThreads=8 \
    -XX:ZCollectionInterval=130 \
    -XX:ZAllocationSpikeTolerance=1 \
    -XX:MaxDirectMemorySize=${JVM_MAX_DIRECT_MEMORY} \
    -XX:MetaspaceSize=${JVM_METASPACE} \
    -XX:MaxMetaspaceSize=${JVM_MAX_METASPACE} \
    -XX:ReservedCodeCacheSize=${JVM_CODE_CACHE} \
    -XX:InitialCodeCacheSize=${JVM_CODE_CACHE} \
    -XX:+UseCountedLoopSafepoints \
    -XX:+SafepointTimeout \
    -XX:SafepointTimeoutDelay=500 \
    -XX:GuaranteedSafepointInterval=0 \
    -XX:+DisableExplicitGC \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/opt/logs/heapdump/ \
    -XX:ZStatisticsInterval=130 \
    -Xlog:safepoint,class+load=info,class+unload=info,classhisto*=trace,age*,gc*=info:file=/opt/logs/gc/gc-%t.log:time,tid,tags:filecount=5,filesize=50m \
    --add-opens java.base/java.lang=ALL-UNNAMED \
    --add-opens java.base/java.io=ALL-UNNAMED \
    --add-opens java.base/java.math=ALL-UNNAMED \
    --add-opens java.base/java.net=ALL-UNNAMED \
    --add-opens java.base/java.nio=ALL-UNNAMED \
    --add-opens java.base/java.security=ALL-UNNAMED \
    --add-opens java.base/java.text=ALL-UNNAMED \
    --add-opens java.base/java.time=ALL-UNNAMED \
    --add-opens java.base/java.util=ALL-UNNAMED \
    --add-opens java.base/java.util.concurrent=ALL-UNNAMED \
    --add-opens java.base/java.util.concurrent.locks=ALL-UNNAMED \
    --add-opens java.base/java.util.concurrent.atomic=ALL-UNNAMED \
    --add-opens java.base/jdk.internal.access=ALL-UNNAMED \
    --add-opens java.base/jdk.internal.misc=ALL-UNNAMED \
    --add-opens java.base/sun.reflect.generics.reflectiveObjects=ALL-UNNAMED \
    --add-opens java.base/jdk.internal.perf=ALL-UNNAMED \
    --add-opens java.base/java.instrument=ALL-UNNAMED \
    --add-opens jdk.attach/sun.tools.attach=ALL-UNNAMED \
    ${JVM_EXTRA_OPTS} \
    -jar app.jar \
"]
