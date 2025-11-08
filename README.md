# FnOSdk

A comprehensive Java SDK for Futures and Options (F&O) trading and simulation on the National Stock Exchange of India (NSE).

## Overview

FnOSdk is a multi-module SDK that provides reusable components for building trading applications, backtesting engines, and simulation tools. It includes core data models, technical analysis indicators, options Greeks calculations, and integration with the Kite Connect API.

## Modules

### fno-models
Core data models and POJOs for trading operations.

**Features:**
- Order models (Index orders, Option orders, Tick-based orders)
- Active order tracking
- Market data structures
- Trading instrument definitions

**Maven Dependency:**
```xml
<dependency>
    <groupId>com.vish.fno</groupId>
    <artifactId>fno-models</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### fno-utils
Utility classes and helper functions for trading operations.

**Features:**
- Candlestick pattern utilities
- Time and date utilities
- File I/O utilities
- Compression utilities
- Order flow strategies (target/stop-loss)
- Heikin-Ashi transformations
- Options metadata utilities

**Maven Dependency:**
```xml
<dependency>
    <groupId>com.vish.fno</groupId>
    <artifactId>fno-utils</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### fno-technicals
Technical analysis indicators and options Greeks calculations.

**Features:**
- Moving Averages (SMA, EMA, Smoothed MA)
- Relative Strength Index (RSI)
- Bollinger Bands
- Options Greeks (Delta, Gamma, Theta, Vega, Rho)
- Extensible indicator framework

**Maven Dependency:**
```xml
<dependency>
    <groupId>com.vish.fno</groupId>
    <artifactId>fno-technicals</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### fno-kite-reader
Integration with Zerodha's Kite Connect API for market data and order execution.

**Features:**
- Historical data retrieval
- Real-time market data via WebSocket
- Order placement and management
- Instrument cache management
- Position and holdings retrieval
- PCR (Put-Call Ratio) utilities

**Maven Dependency:**
```xml
<dependency>
    <groupId>com.vish.fno</groupId>
    <artifactId>fno-kite-reader</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Requirements

- Java 17 or higher
- Maven 3.6 or higher
- Spring Boot 3.2.2 (managed by parent POM)

## Installation

### Clone and Install Locally

```bash
git clone <repository-url>
cd FnOSdk
mvn clean install
```

This will install all modules to your local Maven repository.

### Use in Your Project

Add the SDK modules as dependencies in your project's `pom.xml`:

```xml
<dependencies>
    <!-- Add only the modules you need -->
    <dependency>
        <groupId>com.vish.fno</groupId>
        <artifactId>fno-models</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>com.vish.fno</groupId>
        <artifactId>fno-utils</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>com.vish.fno</groupId>
        <artifactId>fno-technicals</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>com.vish.fno</groupId>
        <artifactId>fno-kite-reader</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
</dependencies>
```

## Build Commands

### Build All Modules
```bash
mvn clean install
```

### Build Individual Module
```bash
cd fno-models
mvn clean install
```

### Run Tests
```bash
mvn test
```

### Run PMD Analysis
```bash
mvn clean package
```

## Usage Examples

### Example 1: Using Technical Indicators

```java
import com.vish.fno.technical.indicators.ma.SimpleMovingAverage;
import com.vish.fno.technical.indicators.RelativeStrengthIndex;

// Calculate Simple Moving Average
SimpleMovingAverage sma = new SimpleMovingAverage(20);
double smaValue = sma.calculate(closePrices);

// Calculate RSI
RelativeStrengthIndex rsi = new RelativeStrengthIndex(14);
double rsiValue = rsi.calculate(closePrices);
```

### Example 2: Fetching Historical Data (Kite Connect)

```java
import com.vish.fno.reader.service.HistoricalDataService;

// Initialize service with Kite Connect credentials
HistoricalDataService service = new HistoricalDataService(kiteConnect);

// Fetch historical candlestick data
List<HistoricalData> data = service.getHistoricalData(
    instrumentToken,
    startDate,
    endDate,
    "day"
);
```

### Example 3: Real-time or Simulation Trading

```java
import com.vish.fno.model.order.orderrequest.IndexOrderRequest;
import com.vish.fno.util.orderflow.FixedTargetAndStopLossStrategy;

// Create order request
IndexOrderRequest orderRequest = IndexOrderRequest.builder()
    .symbol("NIFTY24SEPFUT")
    .quantity(50)
    .orderType("MARKET")
    .transactionType("BUY")
    .build();

// Apply target/stop-loss strategy
TargetAndStopLossStrategy strategy = new FixedTargetAndStopLossStrategy(100, 50);
```

## Code Quality

This SDK follows strict code quality standards:

- PMD static analysis with custom rulesets
- Clean code principles
- Comprehensive unit tests
- Spring Boot best practices
- Java 17 modern features

PMD rules are configured in `ruleset/pmd-custom-ruleset.xml` and run automatically during the build.

## Architecture

### Dependency Hierarchy

```
fno-kite-reader
    └── fno-utils
        └── fno-models

fno-technicals
    ├── fno-utils
    │   └── fno-models
    └── fno-models
```

### Module Responsibilities

- **fno-models**: Foundation layer with core data structures
- **fno-utils**: Business logic utilities and helper functions
- **fno-technicals**: Technical analysis and mathematical calculations
- **fno-kite-reader**: External API integration for live trading

## Use Cases

1. **Live Trading Applications**: Use fno-kite-reader for real-time market data and order execution
2. **Backtesting Engines**: Use fno-models, fno-utils, and fno-technicals for simulation
3. **Technical Analysis Tools**: Use fno-technicals for indicator calculations
4. **Portfolio Management**: Use fno-models and fno-utils for position tracking
5. **Strategy Development**: Combine all modules for comprehensive trading strategies

## Configuration

### Kite Connect API Setup

To use the fno-kite-reader module, you need:

1. Zerodha Kite Connect API credentials
2. Application configuration with API key and access token

```yaml
kite:
  api-key: your-api-key
  access-token: your-access-token
```

## Contributing

This SDK is designed to be extensible:

- Add new technical indicators by extending `AbstractIndicator`
- Create custom order types by implementing `OrderRequest`
- Build new utilities in fno-utils following existing patterns

## License

[Specify your license here]

## Support

For issues, questions, or contributions, please refer to the main project repository.

## Version History

- **1.0.0-SNAPSHOT**: Initial SDK release extracted from OptionsAnalyzer project
  - Core models for orders and market data
  - Technical indicators (MA, RSI, Bollinger Bands)
  - Options Greeks calculations
  - Kite Connect integration
  - Utility classes for trading operations
