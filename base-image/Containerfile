FROM registry.access.redhat.com/ubi8/openjdk-11:1.3-6

ENV CONF_DIR=/opt/cryostat.d

RUN mkdir -p $CONF_DIR

ENV SSL_TRUSTSTORE=$CONF_DIR/truststore.p12 \
    SSL_TRUSTSTORE_PASS_FILE=$CONF_DIR/truststore.pass

COPY include $CONF_DIR

RUN $CONF_DIR/truststore-setup.sh
