# ETCD Configuration Example

This example demonstrates how to use ETCD as the configuration center for dynamic thread pool.

## Prerequisites

1. Start ETCD server:

```bash
# Using Docker
docker run -d --name etcd \
  -p 2379:2379 \
  -p 2380:2380 \
  quay.io/coreos/etcd:v3.5.9 \
  /usr/local/bin/etcd \
  --name s1 \
  --data-dir /etcd-data \
  --listen-client-urls http://0.0.0.0:2379 \
  --advertise-client-urls http://0.0.0.0:2379 \
  --listen-peer-urls http://0.0.0.0:2380 \
  --initial-advertise-peer-urls http://0.0.0.0:2380 \
  --initial-cluster s1=http://0.0.0.0:2380 \
  --initial-cluster-token tkn \
  --initial-cluster-state new
```

2. Put configuration into ETCD:

```bash
# Using etcdctl
etcdctl put /dynamic-thread/config '
dynamic-thread:
  executors:
    - thread-pool-id: order-executor
      core-pool-size: 10
      maximum-pool-size: 20
      queue-capacity: 1000
      keep-alive-time: 60
      rejected-handler: CallerRunsPolicy
    - thread-pool-id: payment-executor
      core-pool-size: 5
      maximum-pool-size: 10
      queue-capacity: 500
      keep-alive-time: 120
      rejected-handler: AbortPolicy
'
```

## Running the Application

```bash
cd dynamic-thread-example/etcd-example
mvn spring-boot:run
```

## Updating Configuration

To update thread pool configuration dynamically:

```bash
# Update configuration in ETCD
etcdctl put /dynamic-thread/config '
dynamic-thread:
  executors:
    - thread-pool-id: order-executor
      core-pool-size: 15
      maximum-pool-size: 30
      queue-capacity: 2000
      keep-alive-time: 60
      rejected-handler: CallerRunsPolicy
    - thread-pool-id: payment-executor
      core-pool-size: 8
      maximum-pool-size: 16
      queue-capacity: 800
      keep-alive-time: 120
      rejected-handler: AbortPolicy
'
```

The application will automatically detect the configuration change and update the thread pools.
