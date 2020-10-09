FROM scratch
FROM ubuntu:18.04 as local_maven
LABEL maintainer="vadim@clusterside.com"
ARG VERSION=2.0.0
WORKDIR /app
RUN apt-get update \
    && apt-get -y upgrade \
    && apt-get -y install apt-utils \
    && apt-get -y install \
        wget \
        python \
        openjdk-8-jdk-headless \
        patch \
        unzip \
        netcat \
        curl \
    && cd / 

RUN mkdir .m2
RUN wget https://atlan-build-artifacts.s3-ap-south-1.amazonaws.com/artifact/maven_local_repository.zip
RUN unzip maven_local_repository.zip -d .m2

FROM maven:3.5-jdk-8-alpine
COPY --from=local_maven /app/.m2 ~/.m2
COPY . /
RUN echo "[INFO] Maven Building"
RUN df
RUN export MAVEN_OPTS="-Xmx4096m -XX:MaxPermSize=1024m"
RUN echo $MAVEN_OPTS
RUN mvn -X  -pl '!addons/sqoop-bridge,!addons/sqoop-bridge-shim' -DskipTests -Drat.skip=true package -Pdist
RUN ls

# RUN echo "[INFO] Maven Building"
# RUN mvn -pl '!addons/sqoop-bridge,!addons/sqoop-bridge-shim' -DskipTests -Drat.skip=true package -Pdist
# RUN echo "[INFO] Listing the directory"
# RUN ls
# 
# RUN mkdir /tmp/atlas-src \
#     && mkdir /opt/ranger-atlas-plugin \
#     && export MAVEN_OPTS="-Xms2g -Xmx2g" \
#     && export JAVA_HOME="/usr/lib/jvm/java-8-openjdk-amd64" \
#     && tar -xzvf distro/target/apache-atlas-3.0.0-SNAPSHOT-server.tar.gz -C /opt \
#     && rm -Rf /tmp/atlas-src \
#     && apt-get clean \
#     && rm -rf /apache-atlas-2.0.0-server.tar.gz
# 
# COPY atlas-hub/atlas_start.py.patch atlas-hub/atlas_config.py.patch /opt/apache-atlas-${VERSION}/bin/
# COPY atlas-hub/pre-conf/atlas-application.properties /opt/apache-atlas-${VERSION}/conf/atlas-application.properties
# COPY atlas-hub/pre-conf/atlas-env.sh /opt/apache-atlas-${VERSION}/conf/atlas-env.sh
# COPY atlas-hub/pre-conf/ranger/lib/ /opt/apache-atlas-${VERSION}/libext/
# COPY atlas-hub/pre-conf/ranger/install/conf.templates/enable/ /opt/apache-atlas-${VERSION}/conf/
# COPY atlas-hub/pre-conf/atlas-log4j.xml /opt/apache-atlas-${VERSION}/conf/
# COPY atlas-hub/pre-conf/ranger/ /opt/ranger-atlas-plugin/
# COPY atlas-hub/env_change.sh /
# 
# RUN cd /opt/apache-atlas-${VERSION}/bin \
#     && patch -b -f < atlas_start.py.patch \
#     && patch -b -f < atlas_config.py.patch \
#     && sed -i "s~ATLAS_INSTALL_DIR~/opt/apache-atlas-${VERSION}~g" /opt/ranger-atlas-plugin/install.properties \ 
#     && chmod +x /env_change.sh
# 
# RUN cd /opt/apache-atlas-${VERSION}/bin \
#     && ./atlas_start.py -setup || true
# 
# VOLUME ["/opt/apache-atlas-2.0.0/conf", "/opt/apache-atlas-2.0.0/logs"]
