package com.redhat.rhjmc.containerjfr.platform.openshift;

import java.nio.file.Files;
import java.nio.file.Paths;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.net.AuthManager;
import com.redhat.rhjmc.containerjfr.platform.internal.PlatformDetectionStrategy;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;

public class OpenShiftPlatformStrategy
        implements PlatformDetectionStrategy<OpenShiftPlatformClient> {

    private final Logger logger;
    private final AuthManager authMgr;
    private final FileSystem fs;
    private OpenShiftClient osClient;

    public OpenShiftPlatformStrategy(
            Logger logger, OpenShiftAuthManager authMgr, Environment env, FileSystem fs) {
        this.logger = logger;
        this.authMgr = authMgr;
        this.fs = fs;
        try {
            this.osClient = new DefaultOpenShiftClient();
        } catch (Exception e) {
            logger.info(e);
            this.osClient = null;
        }
    }

    @Override
    public int getPriority() {
        return PRIORITY_PLATFORM + 15;
    }

    @SuppressFBWarnings("DMI_HARDCODED_ABSOLUTE_FILENAME")
    @Override
    public boolean isAvailable() {
        logger.trace("Testing OpenShift Platform Availability");
        if (osClient == null) {
            return false;
        }
        try {
            // if we aren't in Kubernetes then we definitely aren't in OpenShift
            if (!Files.exists(Paths.get(Config.KUBERNETES_NAMESPACE_PATH))) {
                return false;
            }
            // OpenShift has Routes but if we're running in a different Kubernetes disto,
            // we should get some exception about an unknown type here
            // TODO verify this assumption in some other Kubernetes
            // ServiceAccount should have sufficient permissions on its own to do this
            osClient.routes().list();
            return true;
        } catch (Exception e) {
            logger.info(e);
            return false;
        }
    }

    @Override
    public OpenShiftPlatformClient getPlatformClient() {
        logger.info("Selected OpenShift Platform Strategy");
        return new OpenShiftPlatformClient(logger, osClient, fs);
    }

    @Override
    public AuthManager getAuthManager() {
        return authMgr;
    }
}
