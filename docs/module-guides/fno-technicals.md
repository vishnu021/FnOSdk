# fno-technicals - Technical Analysis

Technical analysis indicators and options Greeks calculations for strategy development and market analysis.

## Maven Dependency
```xml
<dependency>
    <groupId>com.vish.fno</groupId>
    <artifactId>fno-technicals</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

**Transitive Dependencies:** fno-models, fno-utils

---

## Architecture

### Base Indicator Interface

```java
public interface Indicator {
    List<Double> calculate(List<Candle> candles);
    List<Double> calculate(List<Candle> candles, List<Candle> prevCandles);
    List<Double> calculateFromClosedPrice(List<Double> candles);
    List<Double> calculateFromClosedPrice(List<Double> candles, List<Double> prevCandles);
}
```

All indicators implement this interface for consistent calculation patterns.

---

### AbstractIndicator Base Class

```java
public abstract class AbstractIndicator implements Indicator {
    public List<Double> getClosedPrices(List<Candle> candles);
    public List<Double> calculate(List<Candle> candles);
    public List<Double> calculate(List<Candle> candles, List<Candle> prevCandles);
}
```

**Functionality:**
- `getClosedPrices()`: Extracts close prices from candles
- `calculate()`: Delegates to `calculateFromClosedPrice()` implementations

**Subclass Contract:**
- Implement `calculateFromClosedPrice(List<Double>)`
- Implement `calculateFromClosedPrice(List<Double>, List<Double>)`

---

## Moving Averages

### MovingAverage Abstract Class

```java
public abstract class MovingAverage extends AbstractIndicator {
    protected final int period;
    protected MovingAverage(int period);
}
```

Base class for all moving average implementations.

---

### Simple Moving Average (SMA)

```java
public class SimpleMovingAverage extends MovingAverage {
    public SimpleMovingAverage(int period);
}
```

**Common periods:** 20, 50, 200

**Usage:**
```java
import com.vish.fno.technical.indicators.ma.SimpleMovingAverage;
import com.vish.fno.model.Candle;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SMAExample {
    public void calculateSMA(List<Candle> candles) {
        SimpleMovingAverage sma20 = new SimpleMovingAverage(20);
        List<Double> smaValues = sma20.calculate(candles);

        double currentSMA = smaValues.get(smaValues.size() - 1);
        double currentPrice = candles.get(candles.size() - 1).close();

        if (currentPrice > currentSMA) {
            log.info("Price above SMA20 - bullish");
        }
    }
}
```

---

### Exponential Moving Average (EMA)

```java
public class ExponentialMovingAverage extends MovingAverage {
    public ExponentialMovingAverage(int period);
}
```

**Formula:** EMA = Price(t) × k + EMA(y) × (1 − k), where k = 2 / (period + 1)

**Common periods:** 12, 26 (MACD), 9 (signal line)

**Usage:**
```java
import com.vish.fno.technical.indicators.ma.ExponentialMovingAverage;
import com.vish.fno.model.Candle;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MACDExample {
    public void calculateMACD(List<Candle> candles) {
        ExponentialMovingAverage ema12 = new ExponentialMovingAverage(12);
        ExponentialMovingAverage ema26 = new ExponentialMovingAverage(26);

        List<Double> ema12Values = ema12.calculate(candles);
        List<Double> ema26Values = ema26.calculate(candles);

        int lastIdx = ema12Values.size() - 1;
        double macd = ema12Values.get(lastIdx) - ema26Values.get(lastIdx);

        log.info("MACD: {}", macd);
    }
}
```

---

### Smoothed Moving Average (SMMA)

```java
public class SmoothedMovingAverage extends MovingAverage {
    public SmoothedMovingAverage(int period);
}
```

**Formula:** SMMA = (lastSMMA × (period − 1) + closedPrice) / period

**Characteristics:**
- Smoother than EMA (slower response to price changes)
- Less sensitive to short-term fluctuations
- Best for long-term trends and reducing false signals

**Common periods:** 20, 50, 200

---

## Technical Indicators

### Relative Strength Index (RSI)

```java
public class RelativeStrengthIndex extends AbstractIndicator {
    public RelativeStrengthIndex(int period);
}
```

**Interpretation:**
- RSI < 30: Oversold (potential buy)
- RSI > 70: Overbought (potential sell)
- RSI = 50: Neutral

**Standard period:** 14

**Usage:**
```java
import com.vish.fno.technical.indicators.RelativeStrengthIndex;
import com.vish.fno.model.Candle;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RSIExample {
    public void analyzeRSI(List<Candle> candles) {
        RelativeStrengthIndex rsi14 = new RelativeStrengthIndex(14);
        List<Double> rsiValues = rsi14.calculate(candles);

        double currentRSI = rsiValues.get(rsiValues.size() - 1);

        if (currentRSI < 30) {
            log.info("Oversold (RSI: {}) - potential buy", currentRSI);
        } else if (currentRSI > 70) {
            log.info("Overbought (RSI: {}) - potential sell", currentRSI);
        }
    }
}
```

---

### Bollinger Bands

```java
public class BollingerBands extends AbstractIndicator {
    public BollingerBands();                          // Default: 20-period, 2.0 multiplier
    public BollingerBands(int duration, double MULTIPLIER);
}
```

**Returns:** `List<Double>` containing bandwidth values (upper band - lower band)

**Internal Calculation:**
- Upper Band = SMA + (multiplier × Standard Deviation)
- Middle Band = SMA
- Lower Band = SMA - (multiplier × Standard Deviation)
- **Returned Value** = Upper Band - Lower Band

**Standard parameters:** 20-period, 2.0 multiplier

**Usage:**
```java
import com.vish.fno.technical.indicators.BollingerBands;
import com.vish.fno.model.Candle;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BBExample {
    public void analyzeBandwidth(List<Candle> candles) {
        BollingerBands bb = new BollingerBands(20, 2.0);
        List<Double> bandwidths = bb.calculate(candles);

        int lastIdx = bandwidths.size() - 1;
        double currentBandwidth = bandwidths.get(lastIdx);
        double previousBandwidth = bandwidths.get(lastIdx - 1);

        if (currentBandwidth < previousBandwidth * 0.7) {
            log.info("Bollinger squeeze - potential breakout coming");
        }
    }
}
```

---

## Options Greeks

### OptionGreek Abstract Class

```java
public abstract class OptionGreek {
    protected static double getDensity(double d1);
    protected static double cumulativeProbability(double d1);
    protected static double calculateD1(double S, double K, double T, double r, double sigma);
}
```

Base class providing Black-Scholes utilities for all Greeks.

---

### BlackScholes

```java
public final class BlackScholes extends OptionGreek {
    public static double calculateOptionPrice(
        double strikePrice,
        double spotPrice,
        double timeToExpiryInYears,
        double riskFreeRate,
        double volatility,
        boolean isCall
    );

    public static double getTimeToExpiryInMinutes(LocalDateTime now, LocalDateTime expiryTime);
}
```

**Parameters:**
- **strikePrice:** Option strike price
- **spotPrice:** Current underlying price
- **timeToExpiryInYears:** Time in years (e.g., 7/365.0 for 7 days)
- **riskFreeRate:** Annual risk-free rate (e.g., 0.06 for 6%)
- **volatility:** Annual implied volatility (e.g., 0.15 for 15%)
- **isCall:** true for Call, false for Put

**Usage:**
```java
import com.vish.fno.technical.greeks.BlackScholes;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OptionPricingExample {
    public void priceOption() {
        double callPrice = BlackScholes.calculateOptionPrice(
            19500.0, 19550.0, 7.0 / 365.0, 0.06, 0.15, true
        );
        log.info("Call price: {}", callPrice);

        double putPrice = BlackScholes.calculateOptionPrice(
            19500.0, 19550.0, 7.0 / 365.0, 0.06, 0.15, false
        );
        log.info("Put price: {}", putPrice);
    }
}
```

---

### Delta - Price Sensitivity

```java
public final class Delta extends OptionGreek {
    public static double calculateDelta(
        double stockPrice,
        double strikePrice,
        double timeToExpiryInYears,
        double riskFreeRate,
        double volatility,
        boolean isCall
    );
}
```

**CRITICAL:** Method name is `calculateDelta()`, parameter is `boolean isCall`.

**Interpretation:**
- Call Delta: 0 to 1 (ITM closer to 1, OTM closer to 0)
- Put Delta: -1 to 0 (ITM closer to -1, OTM closer to 0)
- ATM Options: Delta ≈ ±0.5

**Meaning:** If delta = 0.5 and spot moves ₹1, option price changes by ₹0.50

**Usage:**
```java
import com.vish.fno.technical.greeks.Delta;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DeltaExample {
    public void calculateDelta() {
        double callDelta = Delta.calculateDelta(19500.0, 19500.0, 7.0 / 365.0, 0.06, 0.15, true);
        log.info("ATM Call Delta: {}", callDelta); // ≈ 0.5

        int optionsHeld = 100;
        int sharesToHedge = (int) Math.abs(callDelta * optionsHeld);
        log.info("Hedge by shorting {} shares", sharesToHedge);
    }
}
```

---

### Gamma - Delta Sensitivity

```java
public final class Gamma extends OptionGreek {
    public static double calculateGamma(
        double stockPrice,
        double strikePrice,
        double timeToExpiryInYears,
        double riskFreeRate,
        double volatility
    );
}
```

**CRITICAL:** Method name is `calculateGamma()`, does NOT require `isCall` (same for calls and puts).

**Interpretation:**
- Range: 0 to positive value (same for calls and puts)
- Meaning: Rate of change of delta per ₹1 move in underlying
- Highest for: ATM options near expiry

---

### Theta - Time Decay

```java
public class Theta extends OptionGreek {
    public static double calculateTheta(
        double stockPrice,
        double strikePrice,
        double timeToExpiryInYears,
        double riskFreeRate,
        double volatility,
        boolean isCall
    );
}
```

**CRITICAL:** Method name is `calculateTheta()`.

**Interpretation:**
- Always negative for long options (options lose value over time)
- Accelerates as expiry approaches (non-linear decay)
- Highest for: ATM options

**Usage:**
```java
import com.vish.fno.technical.greeks.Theta;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ThetaExample {
    public void analyzeThetaDecay() {
        double callTheta = Theta.calculateTheta(19500.0, 19500.0, 7.0 / 365.0, 0.06, 0.15, true);
        log.info("Option loses ₹{} per day due to time decay", Math.abs(callTheta));
    }
}
```

---

### Vega - Volatility Sensitivity

```java
public class Vega extends OptionGreek {
    public static double calculateVega(
        double stockPrice,
        double strikePrice,
        double timeToExpiryInYears,
        double riskFreeRate,
        double volatility
    );
}
```

**CRITICAL:** Method name is `calculateVega()`, does NOT require `isCall` (same for calls and puts).

**Interpretation:**
- Always positive for long options (same for calls and puts)
- Meaning: Change in option price for 1% change in IV
- Highest for: ATM options with more time to expiry

**Usage:**
```java
import com.vish.fno.technical.greeks.Vega;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VegaExample {
    public void analyzeVega() {
        double vega = Vega.calculateVega(19500.0, 19500.0, 7.0 / 365.0, 0.06, 0.15);

        double ivIncrease = 0.01; // 1% increase in IV
        double expectedProfit = vega * ivIncrease;
        log.info("If IV increases 1%, option gains ₹{}", expectedProfit);
    }
}
```

---

### Rho - Interest Rate Sensitivity

```java
public final class Rho extends OptionGreek {
    public static double calculateRho(
        double stockPrice,
        double strikePrice,
        double timeToExpiryInYears,
        double riskFreeRate,
        double volatility,
        boolean isCall
    );
}
```

**CRITICAL:** Method name is `calculateRho()`.

**Interpretation:**
- Call Rho: Positive (calls benefit from higher rates)
- Put Rho: Negative (puts lose from higher rates)
- Least important Greek for short-term options
- Relevant for: Long-dated options (LEAPS, 6+ months)

---

### ImpliedVolatilitySolver - Newton-Raphson IV Solver

```java
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ImpliedVolatilitySolver {
    public static OptionalDouble solve(double strike, double spot, double timeToExpiry,
                                        double riskFreeRate, double marketPrice, boolean isCall);
}
```

Newton-Raphson solver that finds the volatility (sigma) making Black-Scholes price equal the market price.

**Parameters:**
- **strike:** Option strike price
- **spot:** Current underlying spot price
- **timeToExpiry:** Time to expiry in years (e.g., 7.0/365.0 for 7 days)
- **riskFreeRate:** Annual risk-free rate as decimal (e.g., 0.065 for 6.5%)
- **marketPrice:** Current market price of the option
- **isCall:** true for call option, false for put option

**Returns:** `OptionalDouble` -- implied volatility as decimal (e.g., 0.15 for 15%), or empty if solver fails.

**Convergence guards:** vol bounded [0.01, 5.0], max 30 iterations, vega floor to prevent division by near-zero, returns `MIN_VOL` (0.01) if market price is below intrinsic value.

**Usage:**
```java
import com.vish.fno.technical.greeks.ImpliedVolatilitySolver;
import com.vish.fno.technical.greeks.BlackScholes;
import lombok.extern.slf4j.Slf4j;
import java.util.OptionalDouble;

@Slf4j
public class IVSolverExample {
    public void solveIV() {
        double strike = 19500.0;
        double spot = 19550.0;
        double tte = 7.0 / 365.0;
        double rfr = 0.065;

        // Get market price of the call option
        double marketPrice = 185.0;

        OptionalDouble iv = ImpliedVolatilitySolver.solve(strike, spot, tte, rfr, marketPrice, true);
        iv.ifPresentOrElse(
            sigma -> {
                log.info("Implied Volatility: {}%", sigma * 100);
                // Verify: price the option with solved IV
                double bsPrice = BlackScholes.calculateOptionPrice(strike, spot, tte, rfr, sigma, true);
                log.info("BS price with solved IV: {} (market: {})", bsPrice, marketPrice);
            },
            () -> log.warn("IV solver did not converge")
        );
    }
}
```

---

## Complete Trading Example

```java
import com.vish.fno.technical.indicators.ma.SimpleMovingAverage;
import com.vish.fno.technical.indicators.RelativeStrengthIndex;
import com.vish.fno.technical.greeks.BlackScholes;
import com.vish.fno.technical.greeks.Delta;
import com.vish.fno.technical.greeks.Gamma;
import com.vish.fno.technical.greeks.Theta;
import com.vish.fno.model.Candle;
import lombok.extern.slf4j.Slf4j;
import java.util.List;

@Slf4j
public class CompleteTradingStrategy {
    private final SimpleMovingAverage sma50 = new SimpleMovingAverage(50);
    private final RelativeStrengthIndex rsi = new RelativeStrengthIndex(14);

    public void analyzeMarket(List<Candle> candles) {
        // Technical indicators
        List<Double> smaValues = sma50.calculate(candles);
        List<Double> rsiValues = rsi.calculate(candles);

        double currentPrice = candles.get(candles.size() - 1).close();
        double currentSMA = smaValues.get(smaValues.size() - 1);
        double currentRSI = rsiValues.get(rsiValues.size() - 1);

        // Entry signal
        if (currentPrice > currentSMA && currentRSI < 70) {
            log.info("Entry signal - price above SMA, RSI not overbought");

            // Options analysis
            double strike = 19500.0;
            double tte = 7.0 / 365.0;
            double rfr = 0.06;
            double iv = 0.15;

            double callPrice = BlackScholes.calculateOptionPrice(currentPrice, strike, tte, rfr, iv, true);
            double delta = Delta.calculateDelta(currentPrice, strike, tte, rfr, iv, true);
            double gamma = Gamma.calculateGamma(currentPrice, strike, tte, rfr, iv);
            double theta = Theta.calculateTheta(currentPrice, strike, tte, rfr, iv, true);
            double vega = Vega.calculateVega(currentPrice, strike, tte, rfr, iv);

            log.info("Call Price: {}, Delta: {}, Gamma: {}, Theta: {}, Vega: {}",
                     callPrice, delta, gamma, theta, vega);
        }
    }
}
```

---

## Thread Safety

All indicator and Greek classes use static methods or immutable state:
- **Thread-safe:** All Greek calculations (Delta, Gamma, Theta, Vega, Rho, BlackScholes, ImpliedVolatilitySolver)
- **Instance-based:** Indicators (create separate instances per thread if needed)
- **No shared state:** Each indicator instance maintains only configuration (period, multiplier)

---

## Performance Considerations

1. **Indicator Calculation:** O(n) where n = number of candles
2. **Greeks Calculation:** O(1) - constant time mathematical formulas
3. **Memory:** Indicators return new List instances, not views
4. **Reuse:** Create indicator instances once, reuse for multiple calculations

---

## Error Handling

**Division by Zero:**
- BollingerBands handles empty price lists gracefully
- Greeks require non-zero volatility and time to expiry

**Invalid Parameters:**
- Negative periods cause undefined behavior
- Negative volatility produces incorrect results
- Zero time to expiry causes NaN in Greeks

**Edge Cases:**
- First N values of moving averages may be -1 or incomplete
- RSI requires sufficient price history (at least period + 1 candles)
- Bollinger Bands returns -1 for bandwidth when insufficient data

---

## Dependencies

**Apache Commons Math3:**
```xml
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-math3</artifactId>
</dependency>
```

Used for normal distribution calculations in OptionGreek base class.

**Transitive Dependencies:** fno-models (Candle data structures), fno-utils

---

## See Also

- **fno-models:** Core data models
- **fno-utils:** Utility functions
- **fno-strategy-utils:** Higher-level strategy utilities
