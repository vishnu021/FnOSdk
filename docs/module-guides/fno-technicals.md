# fno-technicals - Technical Analysis

## Purpose
Technical analysis indicators and options Greeks calculations for strategy development and market analysis.

## Maven Dependency
```xml
<dependency>
    <groupId>com.vish.fno</groupId>
    <artifactId>fno-technicals</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

This automatically includes `fno-models` and `fno-utils` as transitive dependencies.

## Technical Indicators

### Simple Moving Average (SMA)

```java
import com.vish.fno.technical.indicators.ma.SimpleMovingAverage;
import com.vish.fno.model.Candle;

SimpleMovingAverage sma20 = new SimpleMovingAverage(20);

// From candlestick data
List<Candle> candles = // ... your OHLC data
List<Double> smaValues = sma20.calculate(candles);

// From close prices only
List<Double> closePrices = candles.stream()
    .map(Candle::close)
    .toList();
List<Double> smaValues = sma20.calculateFromClosedPrice(closePrices);

// Get current SMA value
double currentSMA = smaValues.get(smaValues.size() - 1);
```

**Common periods**: 20, 50, 200

### Exponential Moving Average (EMA)

```java
import com.vish.fno.technical.indicators.ma.ExponentialMovingAverage;
import com.vish.fno.model.Candle;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MACDCalculator {
    public void calculateMACD(List<Candle> candles) {
        ExponentialMovingAverage ema12 = new ExponentialMovingAverage(12);
        ExponentialMovingAverage ema26 = new ExponentialMovingAverage(26);

        List<Double> ema12Values = ema12.calculate(candles);
        List<Double> ema26Values = ema26.calculate(candles);

        // MACD calculation (EMA12 - EMA26)
        int lastIdx = ema12Values.size() - 1;
        double macd = ema12Values.get(lastIdx) - ema26Values.get(lastIdx);

        if (macd > 0) {
            log.info("Bullish MACD crossover");
        }
    }
}
```

**Formula**: EMA = Price(t) × k + EMA(y) × (1 − k), where k = 2 / (period + 1)

**Common periods**: 12, 26 (for MACD), 9 (signal line)

### Smoothed Moving Average (SMMA)

```java
import com.vish.fno.technical.indicators.ma.SmoothedMovingAverage;
import com.vish.fno.model.Candle;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SMMAAnalyzer {
    public void analyzeTrend(List<Candle> candles, double currentPrice) {
        SmoothedMovingAverage smma20 = new SmoothedMovingAverage(20);

        List<Double> smmaValues = smma20.calculate(candles);
        double currentSMMA = smmaValues.get(smmaValues.size() - 1);

        // SMMA is smoother than EMA, better for long-term trends
        if (currentPrice > currentSMMA) {
            log.info("Price above SMMA - bullish trend");
        }
    }
}
```

**Formula**: SMMA = (lastSMMA × (period − 1) + closedPrice) / period

**Characteristics**:
- **Smoother** than EMA - slower to respond to price changes
- **Less sensitive** to short-term fluctuations
- **Better for long-term trends** - filters out noise effectively
- **Calculation**: Uses 1/period as multiplier (vs EMA's 2/(period+1))

**When to use**:
- Long-term trend identification
- Reducing false signals in choppy markets
- Smoothing volatile price data

**Differences from EMA**:
- **Smoothing**: SMMA is smoother, EMA reacts faster
- **Sensitivity**: EMA is more sensitive to recent prices
- **Accuracy**: EMA generally more accurate for short-term analysis
- **Usage**: SMMA for long-term, EMA for short-term

**Common periods**: 20, 50, 200

### Relative Strength Index (RSI)

```java
import com.vish.fno.technical.indicators.RelativeStrengthIndex;
import com.vish.fno.model.Candle;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RSIAnalyzer {
    public void analyzeRSI(List<Candle> candles) {
        RelativeStrengthIndex rsi14 = new RelativeStrengthIndex(14);
        List<Double> rsiValues = rsi14.calculate(candles);

        double currentRSI = rsiValues.get(rsiValues.size() - 1);

        if (currentRSI < 30) {
            log.info("Oversold - potential buy signal");
        } else if (currentRSI > 70) {
            log.info("Overbought - potential sell signal");
        }
    }
}
```

**Interpretation:**
- **RSI < 30**: Oversold (potential buy)
- **RSI > 70**: Overbought (potential sell)
- **RSI = 50**: Neutral

**Standard period**: 14

### Bollinger Bands

```java
import com.vish.fno.technical.indicators.BollingerBands;
import com.vish.fno.model.Candle;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BollingerAnalyzer {
    public void analyzeBands(List<Candle> candles) {
        BollingerBands bb = new BollingerBands(20, 2.0); // 20-period, 2 std dev
        Map<String, List<Double>> bands = bb.calculate(candles);

        List<Double> upperBand = bands.get("upper");
        List<Double> middleBand = bands.get("middle");  // SMA
        List<Double> lowerBand = bands.get("lower");

        int lastIdx = upperBand.size() - 1;
        double currentPrice = candles.get(candles.size() - 1).close();

        if (currentPrice >= upperBand.get(lastIdx)) {
            log.info("Price at upper band - overbought");
        } else if (currentPrice <= lowerBand.get(lastIdx)) {
            log.info("Price at lower band - oversold");
        }

        // Calculate bandwidth (volatility measure)
        double bandwidth = (upperBand.get(lastIdx) - lowerBand.get(lastIdx)) / middleBand.get(lastIdx);
    }
}
```

**Components:**
- **Upper Band**: SMA + (2 × Standard Deviation)
- **Middle Band**: 20-period SMA
- **Lower Band**: SMA - (2 × Standard Deviation)

**Standard parameters**: 20-period, 2 std dev

## Options Greeks

All Greeks use the Black-Scholes model with the same parameter structure:

```java
double calculate(
    double spotPrice,      // Current price of underlying
    double strikePrice,    // Option strike price
    double timeToExpiry,   // Time in years (e.g., 7/365 for 7 days)
    double riskFreeRate,   // Annual rate (e.g., 0.06 for 6%)
    double volatility,     // Annual IV (e.g., 0.15 for 15%)
    String optionType      // "CE" for Call, "PE" for Put
)
```

### Delta - Price Sensitivity

```java
import com.vish.fno.technical.greeks.Delta;

double spot = 19500.0;
double strike = 19500.0;
double tte = 7.0 / 365.0;     // 7 days to expiry
double rfr = 0.06;             // 6% risk-free rate
double iv = 0.15;              // 15% implied volatility

double delta = Delta.calculate(spot, strike, tte, rfr, iv, "CE");
// ATM call delta ≈ 0.5
```

**Interpretation:**
- **Call Delta**: 0 to 1 (ITM calls closer to 1, OTM closer to 0)
- **Put Delta**: -1 to 0 (ITM puts closer to -1, OTM closer to 0)
- **ATM Options**: Delta ≈ 0.5 for calls, -0.5 for puts
- **Meaning**: If delta = 0.5 and spot moves ₹1, option price changes by ₹0.50

### Gamma - Delta Sensitivity

```java
import com.vish.fno.technical.greeks.Gamma;

double gamma = Gamma.calculate(spot, strike, tte, rfr, iv, "CE");
```

**Interpretation:**
- **Range**: 0 to positive value
- **Meaning**: Rate of change of delta
- **Highest for**: ATM options with near expiry
- **Usage**: Gamma risk management for option sellers

### Theta - Time Decay

```java
import com.vish.fno.technical.greeks.Theta;

double theta = Theta.calculate(spot, strike, tte, rfr, iv, "CE");
// Typical value: -5.5 means loses ₹5.50 per day
```

**Interpretation:**
- **Always negative** for long options
- **Accelerates** as expiry approaches
- **Highest for**: ATM options
- **Usage**: Premium collection strategies (sell high theta options)

### Vega - Volatility Sensitivity

```java
import com.vish.fno.technical.greeks.Vega;

double vega = Vega.calculate(spot, strike, tte, rfr, iv, "CE");
// vega = 15.2 means ₹15.20 gain if IV increases by 1%
```

**Interpretation:**
- **Always positive** for long options
- **Meaning**: Change in option price for 1% change in IV
- **Highest for**: ATM options with more time to expiry
- **Usage**: Volatility trading strategies

### Rho - Interest Rate Sensitivity

```java
import com.vish.fno.technical.greeks.Rho;

double rho = Rho.calculate(spot, strike, tte, rfr, iv, "CE");
```

**Interpretation:**
- **Least important** Greek for short-term options
- **Relevant for**: Long-dated options (LEAPS)

## Common Trading Strategies

### Strategy 1: Moving Average Crossover

```java
import com.vish.fno.technical.indicators.ma.SimpleMovingAverage;
import com.vish.fno.model.Candle;

public class MACrossoverStrategy {
    private final SimpleMovingAverage sma50 = new SimpleMovingAverage(50);
    private final SimpleMovingAverage sma200 = new SimpleMovingAverage(200);

    public String getSignal(List<Candle> candles) {
        List<Double> sma50Values = sma50.calculate(candles);
        List<Double> sma200Values = sma200.calculate(candles);

        int lastIdx = sma50Values.size() - 1;
        int prevIdx = lastIdx - 1;

        double sma50Current = sma50Values.get(lastIdx);
        double sma200Current = sma200Values.get(lastIdx);
        double sma50Previous = sma50Values.get(prevIdx);
        double sma200Previous = sma200Values.get(prevIdx);

        // Golden cross: SMA50 crosses above SMA200
        if (sma50Current > sma200Current && sma50Previous <= sma200Previous) {
            return "BUY";  // Bullish
        }

        // Death cross: SMA50 crosses below SMA200
        if (sma50Current < sma200Current && sma50Previous >= sma200Previous) {
            return "SELL";  // Bearish
        }

        return "HOLD";
    }
}
```

### Strategy 2: RSI with Bollinger Bands

```java
import com.vish.fno.technical.indicators.RelativeStrengthIndex;
import com.vish.fno.technical.indicators.BollingerBands;
import com.vish.fno.model.Candle;

public class RSIBollingerStrategy {
    private final RelativeStrengthIndex rsi = new RelativeStrengthIndex(14);
    private final BollingerBands bb = new BollingerBands(20, 2.0);

    public boolean isOversold(List<Candle> candles) {
        List<Double> rsiValues = rsi.calculate(candles);
        Map<String, List<Double>> bands = bb.calculate(candles);

        int lastIdx = rsiValues.size() - 1;
        double currentRSI = rsiValues.get(lastIdx);
        double currentPrice = candles.get(candles.size() - 1).getClose();
        double lowerBand = bands.get("lower").get(lastIdx);

        // Strong oversold: RSI < 30 AND price at/below lower band
        return currentRSI < 30 && currentPrice <= lowerBand;
    }

    public boolean isOverbought(List<Candle> candles) {
        List<Double> rsiValues = rsi.calculate(candles);
        Map<String, List<Double>> bands = bb.calculate(candles);

        int lastIdx = rsiValues.size() - 1;
        double currentRSI = rsiValues.get(lastIdx);
        double currentPrice = candles.get(candles.size() - 1).getClose();
        double upperBand = bands.get("upper").get(lastIdx);

        // Strong overbought: RSI > 70 AND price at/above upper band
        return currentRSI > 70 && currentPrice >= upperBand;
    }
}
```

### Strategy 3: Options Premium Collection

```java
public class PremiumCollectionStrategy {
    public List<Double> findOptimalStrikes(double spot) {
        List<Double> optimalStrikes = new ArrayList<>();
        double rfr = 0.06;
        double iv = 0.15;
        double tte = 7.0 / 365.0;

        // Scan OTM put strikes
        for (double strike = spot - 500; strike < spot; strike += 50) {
            double delta = Delta.calculate(spot, strike, tte, rfr, iv, "PE");
            double theta = Theta.calculate(spot, strike, tte, rfr, iv, "PE");
            double gamma = Gamma.calculate(spot, strike, tte, rfr, iv, "PE");

            // Criteria: High theta decay, low gamma risk, delta < 0.30
            if (Math.abs(theta) > 5 &&
                Math.abs(gamma) < 0.001 &&
                Math.abs(delta) < 0.30) {
                optimalStrikes.add(strike);
            }
        }

        return optimalStrikes;
    }
}
```

### Strategy 4: MACD with RSI Confirmation

```java
import com.vish.fno.technical.indicators.ma.ExponentialMovingAverage;
import com.vish.fno.technical.indicators.RelativeStrengthIndex;
import com.vish.fno.model.Candle;

public class MACDRSIStrategy {
    private final ExponentialMovingAverage ema12 = new ExponentialMovingAverage(12);
    private final ExponentialMovingAverage ema26 = new ExponentialMovingAverage(26);
    private final RelativeStrengthIndex rsi = new RelativeStrengthIndex(14);

    public boolean isBuySignal(List<Candle> candles) {
        List<Double> ema12Values = ema12.calculate(candles);
        List<Double> ema26Values = ema26.calculate(candles);
        List<Double> rsiValues = rsi.calculate(candles);

        int lastIdx = ema12Values.size() - 1;

        // Calculate MACD
        double macdCurrent = ema12Values.get(lastIdx) - ema26Values.get(lastIdx);
        double macdPrevious = ema12Values.get(lastIdx - 1) - ema26Values.get(lastIdx - 1);

        // MACD crossed above zero AND RSI confirms (not overbought)
        return macdCurrent > 0 &&
               macdPrevious <= 0 &&
               rsiValues.get(lastIdx) < 70;
    }
}
```

## Best Practices

1. **Validate data length**: Ensure sufficient data points (period + buffer)
   ```java
   if (candles.size() < period) {
       throw new IllegalArgumentException("Insufficient data");
   }
   ```

2. **Handle edge cases**: First N values will be null/NaN for N-period indicators

3. **Combine indicators**: Use multiple indicators for confirmation (e.g., RSI + Bollinger Bands)

4. **Backtesting**: Always backtest strategies before live trading

5. **Greeks assumptions**:
   - Risk-free rate: Use current repo rate (typically 5-7%)
   - Implied volatility: Calculate from option chain or use historical volatility
   - Time to expiry: Use exact days/365 or trading days/252

## Performance Considerations

- **Caching**: Store indicator values instead of recalculating
- **Partial updates**: For real-time data, implement incremental calculation
- **Memory**: Large datasets may require streaming calculation

## Thread Safety

All indicator classes are **not thread-safe**. Create separate instances per thread or use synchronization.

```java
// Thread-safe usage
ThreadLocal<SimpleMovingAverage> sma = ThreadLocal.withInitial(() -> new SimpleMovingAverage(20));
```
