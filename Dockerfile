FROM scratch
FROM ubuntu:18.04
LABEL maintainer="engineering@atlan.com"
ARG VERSION=3.0.0-SNAPSHOT

COPY distro/target/apache-atlas-3.0.0-SNAPSHOT-server.tar.gz  /apache-atlas-3.0.0-SNAPSHOT-server.tar.gz

COPY distro/target/atlas-index-repair-tool-3.0.0-SNAPSHOT.tar.gz /atlas-index-repair-tool-3.0.0-SNAPSHOT.tar.gz 

RUN apt-get update \
    && apt-get -y upgrade \
    && apt-get -y install apt-utils \
    && apt-get -y install \
        wget \
        python \
        openjdk-8-jdk-headless \
        patch \
        netcat \
        curl \
    && cd / \
    && mkdir /opt/ranger-atlas-plugin \
    && export MAVEN_OPTS="-Xms2g -Xmx2g" \
    && export JAVA_HOME="/usr/lib/jvm/java-8-openjdk-amd64" \
    && tar -xzvf /apache-atlas-3.0.0-SNAPSHOT-server.tar.gz -C /opt \
    && mv /opt/apache-atlas-${VERSION} /opt/apache-atlas \
    && apt-get clean \
    && rm -rf /apache-atlas-3.0.0-SNAPSHOT-server.tar.gz

# Copy the repair index jar file
RUN cd / \
    && tar -xzvf /atlas-index-repair-tool-3.0.0-SNAPSHOT.tar.gz \
    && mkdir /opt/apache-atlas/libext \
    && mv /atlas-index-repair-tool-${VERSION}.jar /opt/apache-atlas/libext/ \
    && rm -rf /atlas-index-repair-tool-3.0.0-SNAPSHOT.tar.gz
COPY pre-conf/atlas-script-application.properties /opt/apache-atlas/conf/atlas-script-application.properties
COPY repair_index.py /opt/apache-atlas/bin/

RUN chmod +x /opt/apache-atlas/bin/repair_index.py

COPY atlas-hub/atlas_start.py.patch atlas_config.py.patch /opt/apache-atlas/bin/
COPY atlas-hub/pre-conf/atlas-application.properties /opt/apache-atlas/conf/atlas-application.properties
COPY atlas-hub/pre-conf/atlas-env.sh /opt/apache-atlas/conf/atlas-env.sh
COPY atlas-hub/pre-conf/ranger/lib/ /opt/apache-atlas/libext/
COPY atlas-hub/pre-conf/ranger/install/conf.templates/enable/ /opt/apache-atlas/conf/
COPY atlas-hub/pre-conf/atlas-log4j.xml /opt/apache-atlas/conf/
COPY atlas-hub/pre-conf/ranger/ /opt/ranger-atlas-plugin/
COPY atlas-hub/env_change.sh /

RUN cd /opt/apache-atlas/bin \
    && patch -b -f < atlas_start.py.patch \
    && patch -b -f < atlas_config.py.patch \
    && sed -i "s~ATLAS_INSTALL_DIR~/opt/apache-atlas~g" /opt/ranger-atlas-plugin/install.properties \ 
    && chmod +x /env_change.sh

RUN cd /opt/apache-atlas/bin \
    && ./atlas_start.py -setup || true

VOLUME ["/opt/apache-atlas/conf", "/opt/apache-atlas/logs"]
