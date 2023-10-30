FROM registry.access.redhat.com/ubi8/openjdk-17-runtime:1.17-4

USER root

RUN microdnf --disableplugin=subscription-manager -y install findutils && \
    microdnf --disableplugin=subscription-manager -y clean all

USER 185

ENV CONF_DIR=/opt/cryostat.d

RUN mkdir -p $CONF_DIR

ENV SSL_TRUSTSTORE=$CONF_DIR/truststore.p12 \
    SSL_TRUSTSTORE_PASS_FILE=$CONF_DIR/truststore.pass

COPY include/truststore-setup.sh $CONF_DIR

COPY include/cryostat.jfc /usr/lib/jvm/jre/lib/jfr/

RUN $CONF_DIR/truststore-setup.sh

USER root
RUN chmod -R g=u $CONF_DIR

USER 185
