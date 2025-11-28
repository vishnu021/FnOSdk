package com.vish.fno.manage.orderflow;

import com.vish.fno.model.cache.OrderCache;
import com.vish.fno.model.strategy.MinuteStrategy;
import com.vish.fno.reader.service.KiteService;
import com.vish.fno.util.helper.DataCache;
import com.vish.fno.util.helper.TimeProvider;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * AbstractStrategyExecutor - Library-friendly base class for strategy execution
 *
 * Provides common infrastructure for executing trading strategies with NO Spring Boot dependencies.
 * Uses Template Method pattern with abstract methods for strategy execution flow.
 *
 * Architecture:
 * - Library-friendly: No Spring dependencies, uses constructor injection for all dependencies
 * - Template Method pattern: Abstract methods define execution flow, concrete in subclasses
 * - Immutable configuration: Trading hours are final LocalTime fields
 *
 * Common functionality provided:
 * - executeOptionStrategy(): Handles OptionBasedStrategy (parses index|option format, resolves ITM/OTM)
 * - processSymbolData(): Processes symbol data and executes strategy logic
 * - isWithinTradingHours(): Validates trading hours (excludes weekends)
 *
 * Abstract methods (implemented by subclasses):
 * - executeStrategies(): Main execution flow (called every minute in production, manually in tests)
 * - executeStrategy(): Single strategy execution (different logic for production vs test)
 *
 * Concrete implementations:
 * - StrategyExecutor: Production version with BOTH MinuteStrategy and OptionBasedStrategy support
 *   - Uses @Scheduled cron for automatic execution
 *   - Calls kiteService.appendIndexITMOptions()
 *   - Checks instanceof OptionBasedStrategy to route to appropriate handler
 *
 * - BacktestingStrategyExecutor: Test version with ONLY MinuteStrategy support
 *   - Called manually in tests (no @Scheduled)
 *   - Simplified logic without option-based strategies
 *   - No kiteService.appendIndexITMOptions() call
 **/
@Slf4j
@RequiredArgsConstructor
@SuppressWarnings("PMD.AvoidCatchingGenericException")
public abstract class AbstractStrategyExecutor {

    protected final KiteService kiteService;
    protected final OrderHandler orderHandler;
    protected final DataCache dataCache;
    protected final OrderCache orderCache;
    protected final List<MinuteStrategy> strategies;
    protected final TimeProvider timeProvider;

    @Getter
    private final LocalTime startTradingHour;

    @Getter
    private final LocalTime endTradingHour;

    /**
     * Core strategy execution logic
     * Called by:
     * - StrategyExecutor.update() (scheduled via @Scheduled cron)
     * - BacktestingStrategyExecutor.executeStrategies() (manual call in tests)
     */
    protected abstract void executeStrategies();

    protected abstract void executeStrategy(MinuteStrategy strategy);

    /**
     * Check if current time is within trading hours
     * Excludes weekends (Saturday, Sunday)
     */
    protected boolean isWithinTradingHours(LocalDateTime now) {
        DayOfWeek day = now.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        return !now.toLocalTime().isBefore(startTradingHour) && !now.toLocalTime().isAfter(endTradingHour);
    }
}
