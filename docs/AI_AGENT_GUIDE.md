# AI Agent Guide for FnOSdk

## Overview

FnOSdk provides comprehensive documentation designed specifically for AI coding assistants (Claude Code, GitHub Copilot, etc.) to help them understand and use the SDK modules correctly when working on trading applications.

## Documentation Structure

### 1. Main Reference: `CLAUDE.md`

Located at the root of the FnOSdk project, this 979-line document contains:

- **Module architecture** and dependency relationships
- **Complete API reference** for all public classes and methods
- **Code examples** for every major feature
- **Integration patterns** for common use cases (backtesting, live trading, technical analysis)
- **Best practices** for AI-generated code

**How to use it:**
When an AI agent needs to:
- Create order requests ’ Search for "IndexOrderRequest" or "OptionBasedOrderRequest" in CLAUDE.md
- Calculate technical indicators ’ Search for "SimpleMovingAverage", "RSI", or "BollingerBands"
- Integrate Kite API ’ Search for "KiteService" or "HistoricalDataService"
- Use utilities ’ Search for "CandleUtils", "TimeUtils", or "OptionsMetaDataUtils"

### 2. User Documentation: `README.md`

High-level SDK overview for human developers:
- Installation instructions
- Module descriptions
- Quick start examples
- Build commands

### 3. Project-Specific Integration

For projects using FnOSdk (like OptionsAnalyzer), add a reference to FnOSdk documentation in their CLAUDE.md:

```markdown
## Related SDK: FnOSdk

**SDK Location**: `/path/to/FnOSdk/`
**SDK Documentation**: See `/path/to/FnOSdk/CLAUDE.md` for comprehensive API reference

When working with:
- Technical indicators ’ See FnOSdk/CLAUDE.md section on fno-technicals
- Order models ’ See FnOSdk/CLAUDE.md section on fno-models
- Utilities ’ See FnOSdk/CLAUDE.md section on fno-utils
- Kite integration ’ See FnOSdk/CLAUDE.md section on fno-kite-reader
```

## How AI Agents Should Use This Documentation

### Scenario 1: User asks to implement a moving average crossover strategy

**AI Agent Action:**
1. Read `/Users/vishnushankar/workspace/FnOSdk/CLAUDE.md`
2. Search for "SimpleMovingAverage" to find API
3. Look at "Integration Patterns" section for backtesting example
4. Generate code using the documented API signatures

**Result:** Code that correctly uses `SimpleMovingAverage(period)` with proper method signatures.

### Scenario 2: User asks to place a futures order via Kite

**AI Agent Action:**
1. Read `/Users/vishnushankar/workspace/FnOSdk/CLAUDE.md`
2. Search for "KiteService" and "IndexOrderRequest"
3. Find the example showing order placement with error handling
4. Generate code using Builder pattern as documented

**Result:** Code that uses `IndexOrderRequest.builder()` correctly with all required fields.

### Scenario 3: User asks to calculate option Greeks

**AI Agent Action:**
1. Read `/Users/vishnushankar/workspace/FnOSdk/CLAUDE.md`
2. Search for "Delta", "Gamma", "Theta", "Vega"
3. Find the complete example with all Greeks
4. Note the parameter order and meaning

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
    public List<Double> calculate(List<Candlestick> candles)
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
1. Check `/Users/vishnushankar/workspace/OptionsAnalyzer/CLAUDE.md`
2. See reference to FnOSdk for technical indicators
3. Read `/Users/vishnushankar/workspace/FnOSdk/CLAUDE.md`
4. Find "RelativeStrengthIndex" section with complete API
5. Generate code:

```java
import com.vish.fno.technical.indicators.RelativeStrengthIndex;

public class CandlestickService {
    public List<Double> calculateRSI(List<Candlestick> candles) {
        RelativeStrengthIndex rsi14 = new RelativeStrengthIndex(14);
        return rsi14.calculate(candles);
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
Expected: Uses `Delta.calculate()` with 6 parameters in correct order

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

The FnOSdk CLAUDE.md file serves as a **comprehensive knowledge base** that AI agents can query to generate correct, production-ready code without trial and error. It's designed to be:

- **Complete**: Every public API is documented
- **Practical**: Every feature has working examples
- **Contextual**: Integration patterns show real-world usage
- **Maintainable**: Updates happen in one place

This approach turns AI coding assistants into **domain experts** for F&O trading applications using FnOSdk.
