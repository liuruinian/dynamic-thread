package com.dynamic.thread.server.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import oshi.SystemInfo;
import oshi.hardware.*;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Binds system metrics from OSHI to Micrometer registry for Prometheus export.
 * Provides CPU, memory, disk, and network metrics.
 */
@Slf4j
@Component
public class SystemMetricsBinder implements MeterBinder {

    private final SystemInfo systemInfo;
    private final HardwareAbstractionLayer hardware;
    private final OperatingSystem operatingSystem;
    
    // CPU tick tracking for usage calculation
    private long[] prevCpuTicks;
    
    // Network tracking for rate calculation
    private final AtomicReference<Long> prevNetworkBytesReceived = new AtomicReference<>(0L);
    private final AtomicReference<Long> prevNetworkBytesSent = new AtomicReference<>(0L);

    public SystemMetricsBinder() {
        this.systemInfo = new SystemInfo();
        this.hardware = systemInfo.getHardware();
        this.operatingSystem = systemInfo.getOperatingSystem();
        this.prevCpuTicks = hardware.getProcessor().getSystemCpuLoadTicks();
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        // CPU Metrics
        bindCpuMetrics(registry);
        
        // Memory Metrics
        bindMemoryMetrics(registry);
        
        // Disk Metrics
        bindDiskMetrics(registry);
        
        // Network Metrics
        bindNetworkMetrics(registry);
        
        log.info("System metrics bound to Micrometer registry for Prometheus export");
    }

    private void bindCpuMetrics(MeterRegistry registry) {
        CentralProcessor processor = hardware.getProcessor();
        
        // CPU Usage (0-1)
        Gauge.builder("system.cpu.usage", () -> {
            long[] currentTicks = processor.getSystemCpuLoadTicks();
            double usage = processor.getSystemCpuLoadBetweenTicks(prevCpuTicks);
            prevCpuTicks = currentTicks;
            return usage;
        }).description("System CPU usage ratio").register(registry);
        
        // CPU Core Count
        Gauge.builder("system.cpu.count", processor::getLogicalProcessorCount)
                .description("Number of logical CPU cores")
                .register(registry);
        
        // System Load Average (1 minute)
        Gauge.builder("system.load.average.1m", () -> {
            double[] loadAvg = processor.getSystemLoadAverage(1);
            return loadAvg[0] >= 0 ? loadAvg[0] : 0;
        }).description("System load average (1 minute)").register(registry);
    }

    private void bindMemoryMetrics(MeterRegistry registry) {
        GlobalMemory memory = hardware.getMemory();
        
        // Total Memory
        Gauge.builder("system.memory.total", memory::getTotal)
                .baseUnit("bytes")
                .description("Total physical memory")
                .register(registry);
        
        // Used Memory
        Gauge.builder("system.memory.used", () -> memory.getTotal() - memory.getAvailable())
                .baseUnit("bytes")
                .description("Used physical memory")
                .register(registry);
        
        // Free Memory
        Gauge.builder("system.memory.free", memory::getAvailable)
                .baseUnit("bytes")
                .description("Available physical memory")
                .register(registry);
        
        // Memory Usage Ratio
        Gauge.builder("system.memory.usage", () -> {
            long total = memory.getTotal();
            return total > 0 ? (double) (total - memory.getAvailable()) / total : 0;
        }).description("Memory usage ratio").register(registry);
        
        // Swap Memory
        VirtualMemory vm = memory.getVirtualMemory();
        Gauge.builder("system.swap.total", vm::getSwapTotal)
                .baseUnit("bytes")
                .description("Total swap memory")
                .register(registry);
        
        Gauge.builder("system.swap.used", vm::getSwapUsed)
                .baseUnit("bytes")
                .description("Used swap memory")
                .register(registry);
    }

    private void bindDiskMetrics(MeterRegistry registry) {
        FileSystem fileSystem = operatingSystem.getFileSystem();
        
        // Register metrics for each file store
        List<OSFileStore> fileStores = fileSystem.getFileStores();
        for (int i = 0; i < fileStores.size(); i++) {
            final int index = i;
            OSFileStore store = fileStores.get(i);
            String mount = store.getMount().replace("\\", "/");
            if (mount.isEmpty()) mount = "/";
            final String mountPoint = mount;
            
            // Skip if no space
            if (store.getTotalSpace() <= 0) continue;
            
            Gauge.builder("system.disk.total", () -> {
                List<OSFileStore> stores = operatingSystem.getFileSystem().getFileStores();
                return index < stores.size() ? stores.get(index).getTotalSpace() : 0;
            }).tag("mount", mountPoint)
                    .baseUnit("bytes")
                    .description("Total disk space")
                    .register(registry);
            
            Gauge.builder("system.disk.used", () -> {
                List<OSFileStore> stores = operatingSystem.getFileSystem().getFileStores();
                if (index < stores.size()) {
                    OSFileStore s = stores.get(index);
                    return s.getTotalSpace() - s.getUsableSpace();
                }
                return 0;
            }).tag("mount", mountPoint)
                    .baseUnit("bytes")
                    .description("Used disk space")
                    .register(registry);
            
            Gauge.builder("system.disk.usage", () -> {
                List<OSFileStore> stores = operatingSystem.getFileSystem().getFileStores();
                if (index < stores.size()) {
                    OSFileStore s = stores.get(index);
                    long total = s.getTotalSpace();
                    return total > 0 ? (double) (total - s.getUsableSpace()) / total : 0;
                }
                return 0;
            }).tag("mount", mountPoint)
                    .description("Disk usage ratio")
                    .register(registry);
        }
        
        // Disk I/O
        Gauge.builder("system.disk.read.bytes", () -> {
            long total = 0;
            for (HWDiskStore disk : hardware.getDiskStores()) {
                total += disk.getReadBytes();
            }
            return total;
        }).baseUnit("bytes")
                .description("Total disk read bytes")
                .register(registry);
        
        Gauge.builder("system.disk.write.bytes", () -> {
            long total = 0;
            for (HWDiskStore disk : hardware.getDiskStores()) {
                total += disk.getWriteBytes();
            }
            return total;
        }).baseUnit("bytes")
                .description("Total disk write bytes")
                .register(registry);
    }

    private void bindNetworkMetrics(MeterRegistry registry) {
        // Total network bytes
        Gauge.builder("system.network.receive.bytes", () -> {
            long total = 0;
            for (NetworkIF net : hardware.getNetworkIFs()) {
                net.updateAttributes();
                total += net.getBytesRecv();
            }
            prevNetworkBytesReceived.set(total);
            return total;
        }).baseUnit("bytes")
                .description("Total network bytes received")
                .register(registry);
        
        Gauge.builder("system.network.transmit.bytes", () -> {
            long total = 0;
            for (NetworkIF net : hardware.getNetworkIFs()) {
                net.updateAttributes();
                total += net.getBytesSent();
            }
            prevNetworkBytesSent.set(total);
            return total;
        }).baseUnit("bytes")
                .description("Total network bytes sent")
                .register(registry);
        
        // TCP Connections
        Gauge.builder("system.network.tcp.connections", () -> 
                (int) operatingSystem.getInternetProtocolStats().getTCPv4Stats().getConnectionsEstablished())
                .description("Number of established TCP connections")
                .register(registry);
    }
}
