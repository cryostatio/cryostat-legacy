ARG BASE_IMAGE=registry.access.redhat.com/ubi8/openjdk-11-runtime
# 1.12-1
ARG IMAGE_TAG=@sha256:ebc549bf521c93ec3d4661a028e59158cbb6a31c00930f964cbbc85862ff84bd
FROM ${BASE_IMAGE}${IMAGE_TAG}

USER root

RUN microdnf --disableplugin=subscription-manager -y install findutils && \
    microdnf --disableplugin=subscription-manager -y clean all

USER 185

ENV CONF_DIR=/opt/cryostat.d

RUN mkdir -p $CONF_DIR

ENV SSL_TRUSTSTORE=$CONF_DIR/truststore.p12 \
    SSL_TRUSTSTORE_PASS_FILE=$CONF_DIR/truststore.pass

COPY include $CONF_DIR

RUN $CONF_DIR/truststore-setup.sh

USER root
RUN chmod -R g=u $CONF_DIR

USER 185
