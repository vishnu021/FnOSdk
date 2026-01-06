# AI Agent Guide for FnOSdk

## Overview

FnOSdk provides comprehensive documentation designed specifically for AI coding assistants (Claude Code, GitHub Copilot, etc.) to help them understand and use the SDK modules correctly when working on trading applications.

## Documentation Structure

### 1. Entry Point: `SDK_USAGE.md`

Located in `docs/`, this document provides:
- Quick reference table for common use cases
- Module dependency structure
- Installation instructions
- Links to detailed module guides

### 2. Module-Specific Guides: `docs/module-guides/`

Each module has a comprehensive guide with complete API documentation:
- **fno-models.md** - Core data models (orders, candles, instruments, Task interface with getLots())
- **fno-utils.md** - Utility functions (candle ops, time utils, order formatting)
- **fno-technicals.md** - Technical indicators and Greeks
- **fno-kite-reader.md** - Kite Connect API integration (diagnostics, WebSocket)
- **fno-strategy-utils.md** - Strategy utilities (trend analysis, CPR, price action, partial profit booking)
- **fno-phase-analyzer.md** - Wyckoff phase identification and market regime detection

### 3. Developer Guide: `CLAUDE.md`

Located at the root, this is for **FnOSdk development only** (not for SDK consumers):
- Build commands and testing
- Module architecture
- Code quality standards (PMD rules)
- Contribution guidelines

**How AI agents should use documentation:**
When an AI agent needs to:
- Create order requests → Read `docs/module-guides/fno-models.md`
- Calculate technical indicators → Read `docs/module-guides/fno-technicals.md`
- Integrate Kite API → Read `docs/module-guides/fno-kite-reader.md`
- Use utilities → Read `docs/module-guides/fno-utils.md`
- Trend analysis / CPR → Read `docs/module-guides/fno-strategy-utils.md`
- Wyckoff phase identification → Read `docs/module-guides/fno-phase-analyzer.md`
- Multi-lot strategy configuration → Read `docs/module-guides/fno-models.md#task-interface`

### 4. Project-Specific Integration

For projects using FnOSdk (like OptionsAnalyzer), reference module guides directly:

```markdown
## SDK Dependencies: FnOSdk

**SDK Location**: `/path/to/FnOSdk/`
**Documentation**: `/path/to/FnOSdk/docs/`

**Module Guides** (for AI agents):
- Technical indicators → `/path/to/FnOSdk/docs/module-guides/fno-technicals.md`
- Order models → `/path/to/FnOSdk/docs/module-guides/fno-models.md`
- Utilities → `/path/to/FnOSdk/docs/module-guides/fno-utils.md`
- Kite integration → `/path/to/FnOSdk/docs/module-guides/fno-kite-reader.md`
- Strategy utilities → `/path/to/FnOSdk/docs/module-guides/fno-strategy-utils.md`
- Wyckoff phases → `/path/to/FnOSdk/docs/module-guides/fno-phase-analyzer.md`

When generating code using FnOSdk, AI agents should read the specific module guide for exact API signatures and examples.
```

## How AI Agents Should Use This Documentation

### Scenario 1: User asks to implement a moving average crossover strategy

**AI Agent Action:**
1. Identify need for technical indicator → requires `fno-technicals` module
2. Read `/Users/vishnushankar/workspace/FnOSdk/docs/module-guides/fno-technicals.md`
3. Find "Simple Moving Average (SMA)" section with API reference
4. Copy exact API signature and review code example
5. Generate code using the documented pattern

**Result:** Code that correctly uses `new SimpleMovingAverage(period)` with proper method signatures.

### Scenario 2: User asks to place a futures order via Kite

**AI Agent Action:**
1. Identify needs: Order model + Kite API → requires `fno-models` and `fno-kite-reader`
2. Read `/Users/vishnushankar/workspace/FnOSdk/docs/module-guides/fno-models.md` for IndexOrderRequest
3. Read `/Users/vishnushankar/workspace/FnOSdk/docs/module-guides/fno-kite-reader.md` for KiteService
4. Review examples showing order placement with error handling
5. Generate code using Builder pattern as documented

**Result:** Code that uses `IndexOrderRequest.builder()` correctly with all required fields and proper exception handling.

### Scenario 3: User asks to calculate option Greeks

**AI Agent Action:**
1. Identify need for Options Greeks → requires `fno-technicals` module
2. Read `/Users/vishnushankar/workspace/FnOSdk/docs/module-guides/fno-technicals.md`
3. Find "Options Greeks" section with Delta, Gamma, Theta, Vega
4. Note the common parameter structure across all Greeks
5. Review interpretation guidelines and examples

**Result:** Code that calls `Delta.calculate(spot, strike, tte, rfr, iv, "CE")` with correct parameter types and order.

## Benefits of This Approach

### For AI Agents:
-  **Accurate API usage** - Exact method signatures provided
-  **Context-aware** - Knows which module provides which functionality
-  **Pattern-based** - Can replicate proven integration patterns
-  **Error handling** - Knows which exceptions to catch
-  **Best practices** - Generates production-ready code

### For Human Developers:
-  **Consistent code** - AI generates code following project conventions
-  **Less debugging** - Correct API usage from the start
-  **Faster development** - AI can scaffold entire features correctly
-  **Documentation as code** - Single source of truth

## Maintaining AI Agent Documentation

### When adding new features:

1. **Update class documentation in CLAUDE.md**
```markdown
#### `NewIndicator` - Description
\`\`\`java
public class NewIndicator extends AbstractIndicator {
    public NewIndicator(int param1, double param2)
    public List<Double> calculate(List<Candle> candles)
}
\`\`\`

**Example:**
\`\`\`java
NewIndicator indicator = new NewIndicator(14, 0.5);
List<Double> values = indicator.calculate(candles);
\`\`\`
```

2. **Add integration pattern if applicable**
```markdown
### Pattern 4: Using NewIndicator for XYZ
\`\`\`java
// Complete working example
\`\`\`
```

3. **Update "Best Practices" if new patterns emerge**

### When deprecating features:

Mark clearly in CLAUDE.md:
```markdown
#### `OldClass` - **DEPRECATED**
**Use `NewClass` instead.**
```

## Example: How Claude Code Uses This

When a user in OptionsAnalyzer asks:
> "Add RSI indicator calculation to the candlestick service"

Claude Code will:
1. Check OptionsAnalyzer's CLAUDE.md or skill configuration
2. Identify FnOSdk dependency for technical indicators
3. Read `/Users/vishnushankar/workspace/FnOSdk/docs/module-guides/fno-technicals.md`
4. Find "Relative Strength Index (RSI)" section with:
   - Complete API: `new RelativeStrengthIndex(14)` and `.calculate(candles)`
   - Parameter explanations (period = 14 typical)
   - Interpretation guide (RSI < 30 = oversold, RSI > 70 = overbought)
   - Working code example
5. Generate code:

```java
import com.vish.fno.technical.indicators.RelativeStrengthIndex;
import com.vish.fno.model.Candle;
import java.util.List;

public class CandlestickService {
    private final RelativeStrengthIndex rsi14 = new RelativeStrengthIndex(14);

    public List<Double> calculateRSI(List<Candle> candles) {
        return rsi14.calculate(candles);
    }

    public String getSignal(List<Candle> candles) {
        List<Double> rsiValues = calculateRSI(candles);
        double currentRSI = rsiValues.get(rsiValues.size() - 1);

        if (currentRSI < 30) return "OVERSOLD";
        if (currentRSI > 70) return "OVERBOUGHT";
        return "NEUTRAL";
    }
}
```

## Testing AI Agent Understanding

To verify an AI agent correctly uses the documentation, test with:

**Test 1: "Calculate 20-period SMA for a list of candles"**
Expected: Uses `SimpleMovingAverage(20)` and `calculate(candles)`

**Test 2: "Place a market order for NIFTY futures"**
Expected: Uses `IndexOrderRequest.builder()` with correct fields

**Test 3: "Calculate delta for an ATM NIFTY call option"**
Expected: Uses `Delta.calculateDelta()` with 6 parameters in correct order (stockPrice, strikePrice, timeToExpiryInYears, riskFreeRate, volatility, isCall)

## Future Enhancements

Potential additions to make documentation even more AI-friendly:

1. **JSON Schema** for request/response objects
2. **OpenAPI spec** for REST endpoints
3. **Decision trees** for choosing between different approaches
4. **Error code reference** with suggested fixes
5. **Performance guidelines** (e.g., "Don't call this in a loop")

## Feedback Loop

If AI agents consistently generate incorrect code:
1. Review CLAUDE.md for clarity in that section
2. Add more examples
3. Clarify ambiguous method signatures
4. Add warnings for common mistakes

## Summary

FnOSdk's **modular documentation** (`docs/module-guides/*.md`) serves as a comprehensive knowledge base that AI agents can query to generate correct, production-ready code without trial and error. The documentation is designed to be:

- **Complete**: Every public API is documented in its module guide
- **Practical**: Every feature has working, tested examples
- **Contextual**: Integration patterns show real-world usage across modules
- **Maintainable**: Automatically updated when code changes (via .claude/agents/fnosdk-doc-watcher)
- **AI-Optimized**: Exact signatures, parameter types, and return values for code generation

**Key Documentation Files:**
- `docs/SDK_USAGE.md` - Entry point with quick reference
- `docs/module-guides/fno-models.md` - Order models, market data structures, Task interface (getLots())
- `docs/module-guides/fno-utils.md` - Utility functions and helpers
- `docs/module-guides/fno-technicals.md` - Technical indicators and Greeks
- `docs/module-guides/fno-kite-reader.md` - Kite Connect API integration (diagnostics, WebSocket)
- `docs/module-guides/fno-strategy-utils.md` - Strategy utilities (CPR, trend analysis, partial profit booking)
- `docs/module-guides/fno-phase-analyzer.md` - Wyckoff phase identification and market regime detection

This modular approach turns AI coding assistants into **domain experts** for F&O trading applications using FnOSdk, while keeping documentation maintainable and always in sync with the code.
