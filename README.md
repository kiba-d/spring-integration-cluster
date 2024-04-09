# Spring integration cluster with ShedLock and persistent message store

This is a Kotlin-based project that uses Gradle for build automation.  
The project is configured with Spring Integration and ShedLock for task scheduling and locking.
Project can be run as a producer  or consumer application with multiple instances depending on the active profile.
```yaml
spring:
  profiles:
#   active: producer
    active: consumer
```

## Discoveries
This part describes the discoveries made during the development of the project.

1. Messages ordering  
A BlockingQueue that is backed by a MessageGroupStore can be used to ensure guaranteed delivery in the face of transaction rollback (assuming the store is transactional) and also to ensure messages are not lost if the process dies (assuming the store is durable). To use the queue across process re-starts, the same group id must be provided, so it needs to be unique but identifiable with a single logical instance of the queue.

2. Messages delivery guarantees  
Only one instance can store or modify a key’s value. All of these operations are atomic, thanks to transaction guarantees.
Rad more: ConcurrentMetadataStore, JdbcMetadataStore, MessageGroupQueue,

3. Observability  
Metrics are available though the Actuator. Example: GET http://localhost:8082/actuator/metrics/spring.integration.channel.queue.size

4. Performance  
Performance in most cases is bounded by hardware and persistent store,
https://docs.spring.io/spring-framework/reference/web/websocket/stomp/configuration-performance.html


5. Scalability  
Consumers can be scaled as parallel instances ofn an application and we can use Poller, TaskExecutor and MessageSource { messageGroupQueue.poll() } to scale vertically.


## Getting Started

These instructions will get you a copy of the project up and running on your local machine for development and testing purposes.

### Prerequisites

- JDK 17
- Gradle
- Redis (for ShedLock and Spring Integration)

### Installing

Clone the repository to your local machine:

```bash
git clone https://github.com/kiba-d/project.git
```

Navigate to the project directory:

```bash
cd project
```

Build the project using Gradle:

```bash
gradle build
```

### Running the tests

To run the tests, use the following command:

```bash
gradle test
```

