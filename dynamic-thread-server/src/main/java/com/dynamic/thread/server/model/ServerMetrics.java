package com.dynamic.thread.server.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Server metrics data model containing CPU, memory, disk, network and JVM information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerMetrics {

    /**
     * CPU information
     */
    private CpuInfo cpu;

    /**
     * Memory information
     */
    private MemoryInfo memory;

    /**
     * Disk information
     */
    private DiskInfo disk;

    /**
     * Network information
     */
    private NetworkInfo network;

    /**
     * JVM information
     */
    private JvmInfo jvm;

    /**
     * Metrics collection timestamp
     */
    private LocalDateTime timestamp;

    /**
     * CPU metrics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CpuInfo {
        /**
         * CPU usage percentage (0-100)
         */
        private double usage;

        /**
         * Number of physical cores
         */
        private int physicalCores;

        /**
         * Number of logical cores
         */
        private int logicalCores;

        /**
         * System load average (1 minute)
         */
        private double loadAverage1;

        /**
         * System load average (5 minutes)
         */
        private double loadAverage5;

        /**
         * System load average (15 minutes)
         */
        private double loadAverage15;

        /**
         * CPU model name
         */
        private String model;

        /**
         * CPU vendor
         */
        private String vendor;

        /**
         * CPU frequency in MHz
         */
        private long frequencyMhz;
    }

    /**
     * Memory metrics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemoryInfo {
        /**
         * Total physical memory in bytes
         */
        private long total;

        /**
         * Used physical memory in bytes
         */
        private long used;

        /**
         * Free physical memory in bytes
         */
        private long free;

        /**
         * Memory usage percentage (0-100)
         */
        private double usagePercent;

        /**
         * Total swap memory in bytes
         */
        private long swapTotal;

        /**
         * Used swap memory in bytes
         */
        private long swapUsed;

        /**
         * Free swap memory in bytes
         */
        private long swapFree;
    }

    /**
     * Disk metrics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiskInfo {
        /**
         * List of disk partitions
         */
        private List<PartitionInfo> partitions;

        /**
         * Total disk read bytes
         */
        private long totalReadBytes;

        /**
         * Total disk write bytes
         */
        private long totalWriteBytes;

        /**
         * Disk read speed in bytes per second
         */
        private long readSpeed;

        /**
         * Disk write speed in bytes per second
         */
        private long writeSpeed;
    }

    /**
     * Disk partition information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartitionInfo {
        /**
         * Mount point (e.g., C:, /home)
         */
        private String mountPoint;

        /**
         * File system type (e.g., NTFS, ext4)
         */
        private String fileSystem;

        /**
         * Total space in bytes
         */
        private long totalSpace;

        /**
         * Used space in bytes
         */
        private long usedSpace;

        /**
         * Free space in bytes
         */
        private long freeSpace;

        /**
         * Usage percentage (0-100)
         */
        private double usagePercent;
    }

    /**
     * Network metrics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NetworkInfo {
        /**
         * List of network interfaces
         */
        private List<NetworkInterfaceInfo> interfaces;

        /**
         * Total bytes received across all interfaces
         */
        private long totalBytesReceived;

        /**
         * Total bytes sent across all interfaces
         */
        private long totalBytesSent;

        /**
         * Total receive rate in bytes per second
         */
        private long receiveRate;

        /**
         * Total send rate in bytes per second
         */
        private long sendRate;

        /**
         * Number of active TCP connections
         */
        private int tcpConnections;
    }

    /**
     * Network interface information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NetworkInterfaceInfo {
        /**
         * Interface name
         */
        private String name;

        /**
         * Display name
         */
        private String displayName;

        /**
         * MAC address
         */
        private String macAddress;

        /**
         * IPv4 addresses
         */
        private List<String> ipv4Addresses;

        /**
         * Bytes received
         */
        private long bytesReceived;

        /**
         * Bytes sent
         */
        private long bytesSent;

        /**
         * Receive rate in bytes per second
         */
        private long receiveRate;

        /**
         * Send rate in bytes per second
         */
        private long sendRate;
    }

    /**
     * JVM metrics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JvmInfo {
        /**
         * JVM name
         */
        private String name;

        /**
         * JVM version
         */
        private String version;

        /**
         * JVM vendor
         */
        private String vendor;

        /**
         * JVM uptime in milliseconds
         */
        private long uptime;

        /**
         * Heap memory used in bytes
         */
        private long heapUsed;

        /**
         * Heap memory max in bytes
         */
        private long heapMax;

        /**
         * Heap memory committed in bytes
         */
        private long heapCommitted;

        /**
         * Heap usage percentage (0-100)
         */
        private double heapUsagePercent;

        /**
         * Non-heap memory used in bytes
         */
        private long nonHeapUsed;

        /**
         * Non-heap memory committed in bytes
         */
        private long nonHeapCommitted;

        /**
         * Total loaded class count
         */
        private long loadedClassCount;

        /**
         * Total unloaded class count
         */
        private long unloadedClassCount;

        /**
         * Live thread count
         */
        private int threadCount;

        /**
         * Peak thread count
         */
        private int peakThreadCount;

        /**
         * Daemon thread count
         */
        private int daemonThreadCount;

        /**
         * Total started thread count
         */
        private long totalStartedThreadCount;

        /**
         * GC count
         */
        private long gcCount;

        /**
         * GC time in milliseconds
         */
        private long gcTime;

        /**
         * List of garbage collectors
         */
        private List<GcInfo> garbageCollectors;
    }

    /**
     * Garbage collector information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GcInfo {
        /**
         * GC name
         */
        private String name;

        /**
         * Collection count
         */
        private long collectionCount;

        /**
         * Collection time in milliseconds
         */
        private long collectionTime;
    }
}
