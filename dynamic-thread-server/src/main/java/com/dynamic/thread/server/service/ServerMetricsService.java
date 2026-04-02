package com.dynamic.thread.server.service;

import com.dynamic.thread.server.model.ServerMetrics;
import com.dynamic.thread.server.model.ServerMetrics.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.*;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;

import java.lang.management.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for collecting server metrics including CPU, memory, disk, network and JVM information.
 * Uses OSHI library for system-level metrics and JDK ManagementFactory for JVM metrics.
 */
@Slf4j
@Service
public class ServerMetricsService {

    private final SystemInfo systemInfo;
    private final HardwareAbstractionLayer hardware;
    private final OperatingSystem operatingSystem;

    // Previous values for calculating rates
    private long[] prevCpuTicks;
    private long prevDiskReadBytes;
    private long prevDiskWriteBytes;
    private final ConcurrentHashMap<String, long[]> prevNetworkBytes = new ConcurrentHashMap<>();
    private long prevTimestamp;

    public ServerMetricsService() {
        this.systemInfo = new SystemInfo();
        this.hardware = systemInfo.getHardware();
        this.operatingSystem = systemInfo.getOperatingSystem();
        
        // Initialize previous values
        this.prevCpuTicks = hardware.getProcessor().getSystemCpuLoadTicks();
        this.prevTimestamp = System.currentTimeMillis();
        
        // Initialize disk counters
        long[] diskBytes = getDiskBytes();
        this.prevDiskReadBytes = diskBytes[0];
        this.prevDiskWriteBytes = diskBytes[1];
        
        // Initialize network counters
        for (NetworkIF net : hardware.getNetworkIFs()) {
            net.updateAttributes();
            prevNetworkBytes.put(net.getName(), new long[]{net.getBytesRecv(), net.getBytesSent()});
        }
    }

    /**
     * Get complete server metrics.
     */
    public ServerMetrics getMetrics() {
        return ServerMetrics.builder()
                .cpu(getCpuInfo())
                .memory(getMemoryInfo())
                .disk(getDiskInfo())
                .network(getNetworkInfo())
                .jvm(getJvmInfo())
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Get CPU information.
     */
    public CpuInfo getCpuInfo() {
        CentralProcessor processor = hardware.getProcessor();
        
        // Calculate CPU usage
        long[] currentTicks = processor.getSystemCpuLoadTicks();
        double cpuUsage = processor.getSystemCpuLoadBetweenTicks(prevCpuTicks) * 100;
        prevCpuTicks = currentTicks;
        
        // Get load average (may not be available on Windows)
        double[] loadAverage = processor.getSystemLoadAverage(3);
        
        CentralProcessor.ProcessorIdentifier id = processor.getProcessorIdentifier();
        
        return CpuInfo.builder()
                .usage(Math.round(cpuUsage * 100.0) / 100.0)
                .physicalCores(processor.getPhysicalProcessorCount())
                .logicalCores(processor.getLogicalProcessorCount())
                .loadAverage1(loadAverage[0] >= 0 ? loadAverage[0] : 0)
                .loadAverage5(loadAverage[1] >= 0 ? loadAverage[1] : 0)
                .loadAverage15(loadAverage[2] >= 0 ? loadAverage[2] : 0)
                .model(id.getName())
                .vendor(id.getVendor())
                .frequencyMhz(id.getVendorFreq() / 1_000_000)
                .build();
    }

    /**
     * Get memory information.
     */
    public MemoryInfo getMemoryInfo() {
        GlobalMemory memory = hardware.getMemory();
        
        long total = memory.getTotal();
        long available = memory.getAvailable();
        long used = total - available;
        
        VirtualMemory vm = memory.getVirtualMemory();
        
        return MemoryInfo.builder()
                .total(total)
                .used(used)
                .free(available)
                .usagePercent(Math.round((double) used / total * 10000.0) / 100.0)
                .swapTotal(vm.getSwapTotal())
                .swapUsed(vm.getSwapUsed())
                .swapFree(vm.getSwapTotal() - vm.getSwapUsed())
                .build();
    }

    /**
     * Get disk information.
     */
    public DiskInfo getDiskInfo() {
        FileSystem fileSystem = operatingSystem.getFileSystem();
        List<OSFileStore> fileStores = fileSystem.getFileStores();
        
        List<PartitionInfo> partitions = new ArrayList<>();
        for (OSFileStore store : fileStores) {
            long total = store.getTotalSpace();
            long free = store.getUsableSpace();
            long used = total - free;
            
            if (total > 0) {
                partitions.add(PartitionInfo.builder()
                        .mountPoint(store.getMount())
                        .fileSystem(store.getType())
                        .totalSpace(total)
                        .usedSpace(used)
                        .freeSpace(free)
                        .usagePercent(Math.round((double) used / total * 10000.0) / 100.0)
                        .build());
            }
        }
        
        // Calculate disk I/O rates
        long currentTime = System.currentTimeMillis();
        long timeDelta = currentTime - prevTimestamp;
        long[] diskBytes = getDiskBytes();
        
        long readSpeed = 0;
        long writeSpeed = 0;
        if (timeDelta > 0) {
            readSpeed = (diskBytes[0] - prevDiskReadBytes) * 1000 / timeDelta;
            writeSpeed = (diskBytes[1] - prevDiskWriteBytes) * 1000 / timeDelta;
        }
        
        prevDiskReadBytes = diskBytes[0];
        prevDiskWriteBytes = diskBytes[1];
        prevTimestamp = currentTime;
        
        return DiskInfo.builder()
                .partitions(partitions)
                .totalReadBytes(diskBytes[0])
                .totalWriteBytes(diskBytes[1])
                .readSpeed(Math.max(0, readSpeed))
                .writeSpeed(Math.max(0, writeSpeed))
                .build();
    }

    /**
     * Get network information.
     */
    public NetworkInfo getNetworkInfo() {
        List<NetworkIF> networkIFs = hardware.getNetworkIFs();
        List<NetworkInterfaceInfo> interfaces = new ArrayList<>();
        
        long totalReceived = 0;
        long totalSent = 0;
        long totalReceiveRate = 0;
        long totalSendRate = 0;
        
        long currentTime = System.currentTimeMillis();
        long timeDelta = currentTime - prevTimestamp;
        
        for (NetworkIF net : networkIFs) {
            net.updateAttributes();
            
            // Skip loopback and virtual interfaces
            if (net.getName().contains("Loopback") || net.getIPv4addr().length == 0) {
                continue;
            }
            
            long bytesRecv = net.getBytesRecv();
            long bytesSent = net.getBytesSent();
            
            long recvRate = 0;
            long sendRate = 0;
            
            long[] prevBytes = prevNetworkBytes.get(net.getName());
            if (prevBytes != null && timeDelta > 0) {
                recvRate = (bytesRecv - prevBytes[0]) * 1000 / timeDelta;
                sendRate = (bytesSent - prevBytes[1]) * 1000 / timeDelta;
            }
            prevNetworkBytes.put(net.getName(), new long[]{bytesRecv, bytesSent});
            
            interfaces.add(NetworkInterfaceInfo.builder()
                    .name(net.getName())
                    .displayName(net.getDisplayName())
                    .macAddress(net.getMacaddr())
                    .ipv4Addresses(Arrays.asList(net.getIPv4addr()))
                    .bytesReceived(bytesRecv)
                    .bytesSent(bytesSent)
                    .receiveRate(Math.max(0, recvRate))
                    .sendRate(Math.max(0, sendRate))
                    .build());
            
            totalReceived += bytesRecv;
            totalSent += bytesSent;
            totalReceiveRate += Math.max(0, recvRate);
            totalSendRate += Math.max(0, sendRate);
        }
        
        // Get TCP connection count
        int tcpConnections = (int) operatingSystem.getInternetProtocolStats().getTCPv4Stats().getConnectionsEstablished();
        
        return NetworkInfo.builder()
                .interfaces(interfaces)
                .totalBytesReceived(totalReceived)
                .totalBytesSent(totalSent)
                .receiveRate(totalReceiveRate)
                .sendRate(totalSendRate)
                .tcpConnections(tcpConnections)
                .build();
    }

    /**
     * Get JVM information.
     */
    public JvmInfo getJvmInfo() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        ClassLoadingMXBean classLoadingMXBean = ManagementFactory.getClassLoadingMXBean();
        
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();
        
        // Collect GC info
        List<GcInfo> gcInfoList = new ArrayList<>();
        long totalGcCount = 0;
        long totalGcTime = 0;
        
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcInfoList.add(GcInfo.builder()
                    .name(gc.getName())
                    .collectionCount(gc.getCollectionCount())
                    .collectionTime(gc.getCollectionTime())
                    .build());
            totalGcCount += gc.getCollectionCount();
            totalGcTime += gc.getCollectionTime();
        }
        
        double heapUsagePercent = heapUsage.getMax() > 0 
                ? (double) heapUsage.getUsed() / heapUsage.getMax() * 100 
                : 0;
        
        return JvmInfo.builder()
                .name(runtime.getVmName())
                .version(runtime.getVmVersion())
                .vendor(runtime.getVmVendor())
                .uptime(runtime.getUptime())
                .heapUsed(heapUsage.getUsed())
                .heapMax(heapUsage.getMax())
                .heapCommitted(heapUsage.getCommitted())
                .heapUsagePercent(Math.round(heapUsagePercent * 100.0) / 100.0)
                .nonHeapUsed(nonHeapUsage.getUsed())
                .nonHeapCommitted(nonHeapUsage.getCommitted())
                .loadedClassCount(classLoadingMXBean.getLoadedClassCount())
                .unloadedClassCount(classLoadingMXBean.getUnloadedClassCount())
                .threadCount(threadMXBean.getThreadCount())
                .peakThreadCount(threadMXBean.getPeakThreadCount())
                .daemonThreadCount(threadMXBean.getDaemonThreadCount())
                .totalStartedThreadCount(threadMXBean.getTotalStartedThreadCount())
                .gcCount(totalGcCount)
                .gcTime(totalGcTime)
                .garbageCollectors(gcInfoList)
                .build();
    }

    /**
     * Get total disk read/write bytes.
     */
    private long[] getDiskBytes() {
        long readBytes = 0;
        long writeBytes = 0;
        
        for (HWDiskStore disk : hardware.getDiskStores()) {
            readBytes += disk.getReadBytes();
            writeBytes += disk.getWriteBytes();
        }
        
        return new long[]{readBytes, writeBytes};
    }
}
