# fno-orderflow - Order Flow Management and Strategy Execution

Framework for executing trading strategies and managing order flow with template method pattern for strategy execution.

## Maven Dependency
```xml
<dependency>
    <groupId>com.vish.fno</groupId>
    <artifactId>fno-orderflow</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

**Dependencies included transitively:**
- fno-models (core data models)
- fno-utils (utility functions)
- fno-kite-reader (Kite Connect API integration)

---

## Overview

**fno-orderflow** provides a library-friendly framework for strategy execution without Spring Boot dependencies. It uses the Template Method pattern to separate common execution infrastructure from strategy-specific logic.

**Key Features:**
- Template Method pattern for extensible strategy execution
- Trading hours validation (excludes weekends)
- Support for both minute-based and option-based strategies
- Constructor injection for all dependencies (no Spring required)
- Immutable configuration with final trading hours

---

## OrderHandler Interface

Contract for handling order execution and management.

```java
public interface OrderHandler
```

**Purpose:** Define order handling methods for processing trading orders within the strategy execution framework.

**Implementations should provide:**
- Order placement logic
- Order modification handling
- Order cancellation processing

**Usage Example:**
```java
import com.vish.fno.manage.orderflow.OrderHandler;
import com.vish.fno.model.order.orderrequest.OrderRequest;
import com.vish.fno.model.order.activeorder.ActiveOrder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MyOrderHandler implements OrderHandler {
    // Implement order handling methods
    public void placeOrder(OrderRequest request) {
        log.info("Placing order: {}", request);
    }

    public void modifyOrder(ActiveOrder order, double newStopLoss) {
        order.setStopLoss(newStopLoss);
        log.info("Modified order: {}", order);
    }

    public void cancelOrder(ActiveOrder order) {
        order.setActive(false);
        log.info("Cancelled order: {}", order);
    }
}
```

---

## AbstractStrategyExecutor

Abstract base class for strategy execution with template method pattern.

```java
@Slf4j
@RequiredArgsConstructor
@SuppressWarnings("PMD.AvoidCatchingGenericException")
public abstract class AbstractStrategyExecutor
```

**Architecture:**
- Library-friendly: No Spring dependencies, constructor injection only
- Template Method pattern: Abstract methods define execution flow
- Immutable configuration: Trading hours are final fields

**Protected Fields:**
- `kiteService`: KiteService
- `orderHandler`: OrderHandler
- `dataCache`: DataCache
- `orderCache`: OrderCache
- `strategies`: List&lt;MinuteStrategy&gt;
- `timeProvider`: TimeProvider
- `startTradingHour`: LocalTime (final)
- `endTradingHour`: LocalTime (final)

**Constructor:**
```java
protected AbstractStrategyExecutor(
    KiteService kiteService,
    OrderHandler orderHandler,
    DataCache dataCache,
    OrderCache orderCache,
    List<MinuteStrategy> strategies,
    TimeProvider timeProvider,
    LocalTime startTradingHour,
    LocalTime endTradingHour
)
```
All dependencies injected via constructor (use @RequiredArgsConstructor from Lombok).

### Methods

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `executeStrategies()` | - | `void` | Core execution logic (abstract, implemented by subclasses) |
| `executeStrategy(MinuteStrategy)` | `strategy` | `void` | Single strategy execution (abstract, implemented by subclasses) |
| `isWithinTradingHours(LocalDateTime)` | `now` | `boolean` | Validates trading hours (excludes weekends) |
| `getStartTradingHour()` | - | `LocalTime` | Returns start trading hour |
| `getEndTradingHour()` | - | `LocalTime` | Returns end trading hour |

### Template Method Pattern

**Common functionality (provided by AbstractStrategyExecutor):**
- `isWithinTradingHours()`: Validates trading hours and excludes weekends

**Abstract methods (implemented by subclasses):**
- `executeStrategies()`: Main execution flow
- `executeStrategy()`: Single strategy execution logic

**Concrete Implementations:**

**StrategyExecutor (Production):**
- Uses @Scheduled cron for automatic execution
- Supports BOTH MinuteStrategy and OptionBasedStrategy
- Calls kiteService.appendIndexITMOptions()
- Routes to appropriate handler based on strategy type

**BacktestingStrategyExecutor (Testing):**
- Manual execution (no @Scheduled)
- Supports ONLY MinuteStrategy
- Simplified logic without option-based strategies
- No kiteService.appendIndexITMOptions() call

### Trading Hours Validation

```java
protected boolean isWithinTradingHours(LocalDateTime now)
```

**Logic:**
1. Excludes weekends (Saturday, Sunday)
2. Checks if current time is between `startTradingHour` and `endTradingHour`
3. Returns `false` if outside trading hours or on weekend

**Usage Example:**
```java
import com.vish.fno.manage.orderflow.AbstractStrategyExecutor;
import com.vish.fno.model.strategy.MinuteStrategy;
import com.vish.fno.model.cache.OrderCache;
import com.vish.fno.reader.service.KiteService;
import com.vish.fno.util.helper.DataCache;
import com.vish.fno.util.helper.TimeProvider;
import lombok.extern.slf4j.Slf4j;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Slf4j
public class MyStrategyExecutor extends AbstractStrategyExecutor {

    public MyStrategyExecutor(
        KiteService kiteService,
        OrderHandler orderHandler,
        DataCache dataCache,
        OrderCache orderCache,
        List<MinuteStrategy> strategies,
        TimeProvider timeProvider
    ) {
        super(
            kiteService,
            orderHandler,
            dataCache,
            orderCache,
            strategies,
            timeProvider,
            LocalTime.of(9, 15),  // Start trading at 9:15 AM
            LocalTime.of(15, 30)  // End trading at 3:30 PM
        );
    }

    @Override
    protected void executeStrategies() {
        LocalDateTime now = timeProvider.now();

        if (!isWithinTradingHours(now)) {
            log.info("Outside trading hours: {}", now);
            return;
        }

        for (MinuteStrategy strategy : strategies) {
            executeStrategy(strategy);
        }
    }

    @Override
    protected void executeStrategy(MinuteStrategy strategy) {
        if (!strategy.getTask().isEnabled()) {
            return;
        }

        // Execute strategy logic
        log.info("Executing strategy: {}", strategy.getTag());
    }
}
```

---

## Integration Patterns

### Strategy Execution Flow

**1. Constructor Injection:**
```java
import com.vish.fno.manage.orderflow.*;
import com.vish.fno.model.strategy.MinuteStrategy;
import com.vish.fno.model.cache.OrderCache;
import com.vish.fno.reader.service.KiteService;
import com.vish.fno.util.helper.DataCache;
import com.vish.fno.util.helper.TimeProvider;
import java.time.LocalTime;

public class StrategyRunner {
    public void setupExecutor() {
        KiteService kiteService = new KiteService(/* ... */);
        OrderHandler orderHandler = new MyOrderHandler();
        DataCache dataCache = new DataCache();
        OrderCache orderCache = new OrderCache(100000.0);
        List<MinuteStrategy> strategies = List.of(/* strategies */);
        TimeProvider timeProvider = LocalDateTime::now;

        AbstractStrategyExecutor executor = new MyStrategyExecutor(
            kiteService, orderHandler, dataCache, orderCache,
            strategies, timeProvider
        );
    }
}
```

**2. Manual Execution (Backtesting):**
```java
import com.vish.fno.manage.orderflow.AbstractStrategyExecutor;
import com.vish.fno.model.strategy.MinuteStrategy;
import lombok.extern.slf4j.Slf4j;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Slf4j
public class BacktestExecutor extends AbstractStrategyExecutor {

    public BacktestExecutor(/* dependencies */) {
        super(/* inject dependencies */,
            LocalTime.of(9, 15),
            LocalTime.of(15, 30)
        );
    }

    @Override
    protected void executeStrategies() {
        // Called manually in backtest loop
        for (MinuteStrategy strategy : strategies) {
            executeStrategy(strategy);
        }
    }

    @Override
    protected void executeStrategy(MinuteStrategy strategy) {
        // Simplified backtesting logic
        log.info("Backtesting strategy: {}", strategy.getTag());
    }

    // Manual execution method
    public void runBacktest(LocalDateTime startDate, LocalDateTime endDate) {
        LocalDateTime current = startDate;

        while (!current.isAfter(endDate)) {
            if (isWithinTradingHours(current)) {
                executeStrategies();
            }
            current = current.plusMinutes(1);
        }
    }
}
```

**3. Production Execution (Scheduled):**
```java
import com.vish.fno.manage.orderflow.AbstractStrategyExecutor;
import com.vish.fno.model.strategy.MinuteStrategy;
import com.vish.fno.model.strategy.OptionBasedStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Slf4j
@Service
public class ProductionExecutor extends AbstractStrategyExecutor {

    public ProductionExecutor(/* Spring-injected dependencies */) {
        super(/* inject dependencies */,
            LocalTime.of(9, 15),
            LocalTime.of(15, 30)
        );
    }

    @Scheduled(cron = "0 * 9-15 * * MON-FRI")
    public void update() {
        executeStrategies();
    }

    @Override
    protected void executeStrategies() {
        LocalDateTime now = timeProvider.now();

        if (!isWithinTradingHours(now)) {
            return;
        }

        // Append ITM options for OptionBasedStrategy
        kiteService.appendIndexITMOptions();

        for (MinuteStrategy strategy : strategies) {
            executeStrategy(strategy);
        }
    }

    @Override
    protected void executeStrategy(MinuteStrategy strategy) {
        if (!strategy.getTask().isEnabled()) {
            return;
        }

        if (strategy instanceof OptionBasedStrategy optionStrategy) {
            // Execute option-based strategy
            log.info("Executing option strategy: {}", optionStrategy.getTag());
        } else {
            // Execute minute-based strategy
            log.info("Executing minute strategy: {}", strategy.getTag());
        }
    }
}
```

---

## Edge Cases

**Trading Hours:**
- Weekends (Saturday, Sunday) are always excluded
- `isWithinTradingHours()` uses inclusive checks for start/end times
- Trading hours are immutable (final fields, set in constructor)

**Strategy Execution:**
- Subclasses must handle null/empty strategy lists
- Abstract methods must be implemented by concrete classes
- TimeProvider allows injecting custom time for testing

**Order Handler:**
- Interface has no default methods - implementations must provide all functionality
- Implementations should handle order lifecycle (place, modify, cancel)

---

## Thread Safety

**AbstractStrategyExecutor:**
- Not thread-safe by default
- Protected fields should be treated as read-only after construction
- Subclasses responsible for thread-safety if executing concurrently

**OrderHandler:**
- Thread-safety depends on concrete implementation
- Implementations should document thread-safety guarantees

---

## Dependencies

- **fno-models**: Core data models (OrderRequest, ActiveOrder, Strategy interfaces)
- **fno-utils**: Utility functions (DataCache, TimeProvider)
- **fno-kite-reader**: Kite Connect API integration (KiteService)
- **Lombok**: For reducing boilerplate (@Slf4j, @RequiredArgsConstructor, @Getter)

---

## See Also

- **fno-models**: Order models and strategy interfaces
- **fno-utils**: Utility functions and helper classes
- **fno-kite-reader**: Kite Connect API integration
- **fno-strategy-utils**: Strategy utilities for order flow management
