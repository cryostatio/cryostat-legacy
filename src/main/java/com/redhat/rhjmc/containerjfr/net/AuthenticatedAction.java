package com.redhat.rhjmc.containerjfr.net;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public interface AuthenticatedAction {
    AuthenticatedAction onSuccess(Runnable runnable);

    AuthenticatedAction onFailure(Runnable runnable);

    void execute() throws InterruptedException, ExecutionException, TimeoutException;
}
