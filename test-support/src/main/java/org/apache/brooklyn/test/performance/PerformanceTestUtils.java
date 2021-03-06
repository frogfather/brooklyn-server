/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.test.performance;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.brooklyn.util.exceptions.RuntimeInterruptedException;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;

public class PerformanceTestUtils {

    private static final Logger LOG = LoggerFactory.getLogger(PerformanceTestUtils.class);

    private static boolean hasLoggedProcessCpuTimeUnavailable;
    private static boolean hasLoggedProcessCpuLoadUnavailable;
    
    public static long getProcessCpuTime() {
        try {
            MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
            ObjectName osMBeanName = ObjectName.getInstance(ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME);
            return (Long) mbeanServer.getAttribute(osMBeanName, "ProcessCpuTime");
        } catch (Exception e) {
            if (!hasLoggedProcessCpuTimeUnavailable) {
                hasLoggedProcessCpuTimeUnavailable = true;
                LOG.warn("ProcessCuuTime not available in local JVM MXBean "+ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME+" (only available in sun JVM?)");
            }
            return -1;
        }
    }
    
    public static double getProcessCpuTime(Duration period) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        long prevCpuTime = getProcessCpuTime();
        if (prevCpuTime==-1) {
            return -1;
        }
        Time.sleep(period);
        long currentCpuTime = getProcessCpuTime();

        long elapsedTime = stopwatch.elapsed(TimeUnit.MILLISECONDS);
        return (elapsedTime > 0) ? ((double)currentCpuTime-prevCpuTime) / TimeUnit.MILLISECONDS.toNanos(elapsedTime) : -1;
    }
    
    /** Not very fine-grained so not very useful; use {@link #getProcessCpuTime(Duration)} */ 
    public static double getProcessCpuAverage() {
        try {
            MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
            ObjectName osMBeanName = ObjectName.getInstance(ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME);
            return (Double) mbeanServer.getAttribute(osMBeanName, "ProcessCpuLoad");
        } catch (Exception e) {
            if (!hasLoggedProcessCpuLoadUnavailable) {
                hasLoggedProcessCpuLoadUnavailable = true;
                LOG.warn("ProcessCpuLoad not available in local JVM MXBean "+ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME+" (only available in sun JVM?)");
            }
            return -1;
        }
    }

    /**
     * Creates a background thread that will log.info the CPU fraction usage repeatedly, sampling at the given period.
     * Callers <em>must</em> cancel the returned future, e.g. {@code future.cancel(true)}, otherwise it will keep
     * logging until the JVM exits.
     */
    public static Future<?> sampleProcessCpuTime(final Duration period, final String loggingContext) {
        return sampleProcessCpuTime(period, loggingContext, null);
    }
    
    public static Future<?> sampleProcessCpuTime(final Duration period, final String loggingContext, final List<Double> cpuFractions) {
        final ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
                @Override public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "brooklyn-sampleProcessCpuTime-"+loggingContext);
                    thread.setDaemon(true); // let the JVM exit
                    return thread;
                }});
        Future<?> future = executor.submit(new Runnable() {
                @Override public void run() {
                    try {
                        if (getProcessCpuTime() == -1) {
                            LOG.warn("ProcessCPuTime not available; cannot sample; aborting");
                            return;
                        }
                        while (true) {
                            Stopwatch timerForReporting = Stopwatch.createStarted();
                            double fractionCpu = getProcessCpuTime(period);
                            long elapsedTime = timerForReporting.elapsed(TimeUnit.MILLISECONDS);
                            LOG.info("CPU fraction over last {} was {} ({})", new Object[] {
                                    Time.makeTimeStringRounded(elapsedTime), ((int)(1000*fractionCpu))/1000.0, loggingContext});
                            
                            if (cpuFractions != null) {
                                cpuFractions.add(fractionCpu);
                            }
                        }
                    } catch (RuntimeInterruptedException e) {
                        return; // graceful termination
                    } finally {
                        executor.shutdownNow();
                    }
                }});
        return future;
    }
}
