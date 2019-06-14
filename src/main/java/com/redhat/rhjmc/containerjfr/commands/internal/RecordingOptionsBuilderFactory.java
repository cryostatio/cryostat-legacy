package com.redhat.rhjmc.containerjfr.commands.internal;

import org.openjdk.jmc.common.unit.QuantityConversionException;
import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;

interface RecordingOptionsBuilderFactory {
    RecordingOptionsBuilder create(IFlightRecorderService service) throws QuantityConversionException;
}