# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

ServiceTool is an Android application for industrial weighing cell communication and configuration. The app communicates with Flintec RC3D weighing cells through a Moxa RS485-to-Ethernet converter using TCP sockets and RS485 protocol.

### Architecture

- **Communication Layer**: `CommunicationManager` handles TCP socket connections to Moxa devices
- **Protocol Layer**: `FlintecRC3DCommands` and `FlintecRC3DMultiCellCommands` implement Flintec RC3D protocol
- **Data Layer**: `FlintecData`, `MultiCellConfig`, and `SettingsManager` handle data persistence and configuration
- **UI Layer**: Fragment-based navigation with drawer layout using Material Design 3
- **Infrastructure**: `LoggingManager` provides comprehensive logging and telemetry

### Key Components

- **MainActivity**: Navigation hub with drawer layout and splash screen
- **CommunicationManager**: Core TCP/RS485 communication with diagnostic capabilities
- **MultiCellConfig**: Multi-cell configuration management with persistent settings
- **MoxaTelnetController**: Telnet-based Moxa device management and configuration
- **Protocol Classes**: Implementation of Flintec RC3D communication protocol with STX/ETX framing

## Build Commands

### Development
```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK  
./gradlew assembleRelease

# Install debug build on connected device
./gradlew installDebug

# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest
```

### Project Setup
- **Target SDK**: 35 (Android 14)
- **Min SDK**: 26 (Android 8.0) - Required for adaptive icons
- **Kotlin**: 1.9.0
- **Android Gradle Plugin**: 8.6.0
- **View Binding**: Enabled

### Key Dependencies
- **Navigation Component**: Fragment navigation with drawer integration
- **Material Design 3**: UI components and theming
- **Apache Commons Net**: Telnet communication for Moxa device management
- **Core Splash Screen**: Unified splash screen implementation

## Communication Protocol

The app implements the Flintec RC3D protocol:
- **Framing**: Commands wrapped with STX (0x02) and ETX (0x03) control characters
- **Transport**: TCP sockets to Moxa RS485-Ethernet converters
- **Multi-cell**: Support for addressing individual cells in multi-cell configurations
- **Diagnostics**: Built-in connection and communication diagnostics

### Network Configuration
- Default Moxa IP: Configurable through settings
- Default Port: 4001 (configurable)
- Timeout: 15 seconds for command responses
- Connection diagnostics include network reachability, Moxa connectivity, and cell communication tests

## Navigation Structure

Five main destinations accessible via navigation drawer:
- **Dashboard**: Main overview and quick actions
- **Multi-Cell Overview**: Real-time multi-cell monitoring and status
- **Cell Configuration**: Individual cell setup and calibration
- **App Settings**: Application preferences and configuration
- **Moxa Settings**: Moxa device network configuration via Telnet

## Settings and Configuration

- **SettingsManager**: SharedPreferences wrapper for app settings
- **MultiCellConfig**: Multi-cell specific configuration with IP/port settings
- **Persistent Storage**: Settings survive app restarts and maintain cell configurations

## Logging and Diagnostics

**LoggingManager** provides:
- Communication logging with direction, timing, and success tracking
- Cell-specific error tracking and diagnostics  
- System event logging with timestamps
- Performance monitoring for network operations

## Development Notes

- The project is currently on branch `V1.103` with master as the main branch
- All fragments use View Binding for type-safe view access  
- Coroutines used extensively for network operations with proper IO dispatching
- Material Design 3 theming with custom color schemes and adaptive icons
- German language UI with comprehensive logging in German