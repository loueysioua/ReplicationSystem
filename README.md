# Distributed Replica System with JavaFX UI

This project is a distributed replica system with a modern JavaFX user interface that allows you to manage multiple replicas dynamically, simulate breakdowns by stopping replicas, and interact with the system.

## Features

- **Dynamic Replica Management**: Add as many replicas as needed and start/stop them at will
- **Failure Simulation**: Stop replicas to simulate breakdowns
- **Modern UI/UX**: Clean and intuitive JavaFX interface
- **Real-time Logging**: View system activity in real-time
- **Distributed Communication**: Uses RabbitMQ for communication between components
- **Persistent Storage**: Each replica stores data in its own SQLite database

## Requirements

- Java 11 or higher
- Maven
- RabbitMQ server running locally (default configuration)

## Project Structure

- **ui/**: Contains JavaFX UI components
  - `ReplicaSystemUI.java`: Main UI application
  - `ReplicaSystemLauncher.java`: Entry point
- **Main/**: Core system components
  - `Replica.java`: Individual replica instances
  - `ClientReader.java`: Client to read last line from replicas
  - `ClientReaderV2.java`: Client to read all lines from replicas
  - `ClientWriter.java`: Client to write data to replicas
- **messaging/**: Communication components
  - `RabbitMQManager.java`: Handles RabbitMQ connections and messaging
- **database/**: Database components
  - `JPAUtil.java`: JPA utility for entity management
  - `TextEntity.java`: JPA entity for stored text
  - `TextRepository.java`: Data access object for text operations
- **config/**: Configuration settings
  - `AppConfig.java`: System configuration constants
- **utils/**: Utility classes
  - `LoggerUtil.java`: Logging utilities

## Setup and Running

1. **Install and start RabbitMQ**:
   Make sure RabbitMQ is installed and running on localhost with default settings.

2. **Build the project**:
   ```
   mvn clean package
   ```

3. **Run the application**:
   ```
   mvn javafx:run
   ```
   
   or
   
   ```
   java -jar target/replica-system-1.0-SNAPSHOT.jar
   ```

## Using the UI

### Main Controls

- **Add New Replica**: Creates a new replica with the next available ID
- **Stop All Replicas**: Stops all currently running replicas
- **Read Last**: Sends command to read the last line from all replicas
- **Read All**: Sends command to read all lines from all replicas
- **Write**: Allows writing new content to a specific line number

### Replica Management

Each replica in the table has individual controls:
- **Status**: Shows whether the replica is running or stopped
- **Stop/Start**: Allows stopping or starting each replica individually

### Log Area

The log area at the bottom shows real-time output from all replicas and system operations, making it easy to track what's happening in the system.

## Architecture

The system uses a publish-subscribe pattern with RabbitMQ:
- All commands are published to a fanout exchange
- Each replica listens to its own queue bound to the exchange
- Replicas process commands and store/retrieve data from their individual SQLite databases

### Command Protocol

- `READ LAST`: Retrieves the last line from the replicas
- `READ ALL`: Retrieves all lines from the replicas
- `WRITE <lineNumber> <content>`: Writes content to the specified line number

## Extending the System

You can extend this system by:
- Adding more command types
- Implementing data replication between replicas
- Adding fault tolerance mechanisms
- Adding authentication and security features
