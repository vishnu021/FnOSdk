package com.vish.fno.strategy;

import com.vish.fno.model.Candle;
import com.vish.fno.util.Trend;
import com.vish.fno.util.candle.CandleUtils;
import com.vish.fno.util.candle.HeikinAshi;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@SuppressWarnings("PMD")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HATrendUtils {
    // CPD-OFF

    private static final int WINDOW_SIZE = 5; // Define the window size for smoothing
    private static final double DOJI_THRESHOLD = 0.1; // Threshold to consider a candle as Doji

    public static Trend getTrend(List<Candle> candles) {
        final List<Trend> trendList = getTrendList(candles);
        return trendList.get(trendList.size() - 1);
    }

    public static List<Point2D> getMaximaMinima(List<Candle> candles) {
        List<Point2D> maximaMinima = new ArrayList<>();
        List<Trend> simplifiedTrends = getSmoothedTrends(candles);

        if (simplifiedTrends.isEmpty() || candles.isEmpty()) {
            return maximaMinima;
        }

        Trend currentTrend = simplifiedTrends.get(0);
        int trendChangeIndex = 0;

        for (int i = 1; i < simplifiedTrends.size(); i++) {
            if (simplifiedTrends.get(i) != currentTrend) {
                List<Candle> candlesInPreviousTrend = candles.subList(trendChangeIndex, i);

                if (currentTrend == Trend.DOWNTREND) {
                    double minima = candlesInPreviousTrend.stream().mapToDouble(Candle::low).min().orElse(Double.MAX_VALUE);
                    for (int j = trendChangeIndex; j < i; j++) {
                        if (candles.get(j).low() == minima) {
                            maximaMinima.add(new Point2D(j, minima, PointType.MINIMA));
                            break;
                        }
                    }
                } else if (currentTrend == Trend.UPTREND) {
                    double maxima = candlesInPreviousTrend.stream().mapToDouble(Candle::high).max().orElse(-Double.MAX_VALUE);
                    for (int j = trendChangeIndex; j < i; j++) {
                        if (candles.get(j).high() == maxima) {
                            maximaMinima.add(new Point2D(j, maxima, PointType.MAXIMA));
                            break;
                        }
                    }
                }

                trendChangeIndex = i;
                currentTrend = simplifiedTrends.get(i);
            }
        }

        return maximaMinima;
    }

    public static List<Trend> getSmoothedTrends(List<Candle> candles) {
        if (candles.isEmpty()) {
            return List.of();
        }

        final List<Candle> haCandles = HeikinAshi.getIntradayCompleteCandle(candles, 1);
        final List<Trend> simplifiedTrend = new ArrayList<>();

        for (final Candle candle : haCandles) {
            if (isDoji(candle)) {
                simplifiedTrend.add(Trend.INDECISIVE);
            } else if (CandleUtils.isBullish(candle)) {
                simplifiedTrend.add(Trend.UPTREND);
            } else {
                simplifiedTrend.add(Trend.DOWNTREND);
            }
        }

        return determineTrends(simplifiedTrend, haCandles);
    }

    private static boolean isStrongBullish(Candle candle) {
        return CandleUtils.isBullish(candle) && candle.low() == candle.open();
    }

    private static boolean isStrongBearish(Candle candle) {
        return CandleUtils.isBearish(candle) && candle.high() == candle.open();
    }

    private static boolean isHigherHighAndHigherLow(Candle current, Candle previous) {
        return current.high() > previous.high() && current.low() > previous.low();
    }

    private static boolean isLowerHighAndLowerLow(Candle current, Candle previous) {
        return current.high() < previous.high() && current.low() < previous.low();
    }

    private static boolean isDoji(Candle candle) {
        double bodySize = Math.abs(candle.close() - candle.open());
        double candleRange = candle.high() - candle.low();
        return bodySize / candleRange < DOJI_THRESHOLD;
    }

    private static List<Trend> determineTrends(List<Trend> simplifiedTrend, List<Candle> haCandles) {
        List<Trend> trendList = new ArrayList<>();
        Trend lastTrend = Trend.INDECISIVE;

        for (int i = 0; i < simplifiedTrend.size(); i++) {
            int uptrendScore = 0;
            int downtrendScore = 0;

            int startIndex = Math.max(0, i - WINDOW_SIZE + 1);

            for (int j = startIndex; j <= i; j++) {
                Trend currentTrend = simplifiedTrend.get(j);
                if (currentTrend == Trend.UPTREND) {
                    if (j > 0 && isHigherHighAndHigherLow(haCandles.get(j), haCandles.get(j - 1))) {
                        uptrendScore += 5; // Higher highs and higher lows get the highest weight
                    } else if (isStrongBullish(haCandles.get(j))) {
                        uptrendScore += 3; // Strong bullish gets a high weight
                    } else if (CandleUtils.isBullish(haCandles.get(j))) {
                        uptrendScore += 1; // Regular bullish
                    }
                } else if (currentTrend == Trend.DOWNTREND) {
                    if (j > 0 && isLowerHighAndLowerLow(haCandles.get(j), haCandles.get(j - 1))) {
                        downtrendScore += 5; // Lower highs and lower lows get the highest weight
                    } else if (isStrongBearish(haCandles.get(j))) {
                        downtrendScore += 3; // Strong bearish gets a high weight
                    } else if (CandleUtils.isBearish(haCandles.get(j))) {
                        downtrendScore += 1; // Regular bearish
                    }
                }
            }

            // Determine the final trend based on the weighted score in the current window
            Trend currentTrend;
            if (uptrendScore > downtrendScore) {
                currentTrend = Trend.UPTREND;
            } else if (downtrendScore > uptrendScore) {
                currentTrend = Trend.DOWNTREND;
            } else {
                currentTrend = lastTrend;
            }

            if (currentTrend != Trend.INDECISIVE) {
                lastTrend = currentTrend;
            }

            trendList.add(currentTrend);
        }

        for(int i = 1; i < trendList.size() - 1; i++) {
            if(trendList.get(i - 1) == trendList.get(i + 1) && trendList.get(i)!= trendList.get(i - 1)) {
                trendList.set(i, trendList.get(i - 1));
            }
        }

        return trendList;
    }

    private static Trend determineCurrentTrend(List<Trend> simplifiedTrend, List<Candle> haCandles) {
        int uptrendScore = 0;
        int downtrendScore = 0;

        int startIndex = Math.max(0, simplifiedTrend.size() - WINDOW_SIZE);

        for (int j = startIndex; j < simplifiedTrend.size(); j++) {
            Trend currentTrend = simplifiedTrend.get(j);
            if (currentTrend == Trend.UPTREND) {
                if (j > 0 && isHigherHighAndHigherLow(haCandles.get(j), haCandles.get(j - 1))) {
                    uptrendScore += 5; // Higher highs and higher lows get the highest weight
                } else if (isStrongBullish(haCandles.get(j))) {
                    uptrendScore += 3; // Strong bullish gets a high weight
                } else if (CandleUtils.isBullish(haCandles.get(j))) {
                    uptrendScore += 1; // Regular bullish
                }
            } else if (currentTrend == Trend.DOWNTREND) {
                if (j > 0 && isLowerHighAndLowerLow(haCandles.get(j), haCandles.get(j - 1))) {
                    downtrendScore += 5; // Lower highs and lower lows get the highest weight
                } else if (isStrongBearish(haCandles.get(j))) {
                    downtrendScore += 3; // Strong bearish gets a high weight
                } else if (CandleUtils.isBearish(haCandles.get(j))) {
                    downtrendScore += 1; // Regular bearish
                }
            }
        }

        // Determine the final trend based on the weighted score in the current window
        Trend lastTrend = Trend.INDECISIVE;
        if (uptrendScore > downtrendScore) {
            lastTrend = Trend.UPTREND;
        } else if (downtrendScore > uptrendScore) {
            lastTrend = Trend.DOWNTREND;
        }

        // If the current trend is indecisive, return the last known trend
        if (lastTrend == Trend.INDECISIVE) {
            return getLastKnownTrend(simplifiedTrend);
        }

        return lastTrend;
    }

    private static Trend getLastKnownTrend(List<Trend> trends) {
        for (int i = trends.size() - 1; i >= 0; i--) {
            Trend trend = trends.get(i);
            if (trend != Trend.INDECISIVE) {
                return trend;
            }
        }
        return Trend.INDECISIVE;
    }


    public static List<Trend> getTrendList(final List<Candle> candles) {
        final List<Candle> haCandles = HeikinAshi.getIntradayCompleteCandle(candles, 1);
        final List<Trend> trends = new ArrayList<>();
        Trend existingTrend = Trend.INDECISIVE;
        int consolidationStartIndex = 0;

        // long wicks on both sides -> indecisive
        // wick in direction, no lower wick, and low and high above last
        // if candle inside last big candle, consolidation

        trends.add(existingTrend);
        for(int i = 1; i < haCandles.size(); i++) {
            Candle lastCandle = haCandles.get(i);
            Candle secondLastCandle = haCandles.get(i - 1);


            // consolidation logic starts
            if(existingTrend == Trend.CONSOLIDATION) {
                List<Candle> consolidationCandles = haCandles.subList(consolidationStartIndex, i);
                double consolidationHigh = consolidationCandles.stream().mapToDouble(Candle::high).max().getAsDouble();
                double consolidationLow = consolidationCandles.stream().mapToDouble(Candle::low).min().getAsDouble();

                if(consolidationHigh < lastCandle.high() && isStronglyBullish(lastCandle)) {
                    existingTrend = Trend.UPTREND;
                    trends.add(existingTrend);
                    continue;
                }

                if(consolidationLow > lastCandle.low() && isStronglyBearish(lastCandle)) {
                    existingTrend = Trend.DOWNTREND;
                    trends.add(existingTrend);
                    continue;
                }

                if(consolidationLow > lastCandle.low() && isStronglyBearish(lastCandle)) {
                    existingTrend = Trend.DOWNTREND;
                    trends.add(existingTrend);
                    continue;
                }

                if(isIndecisive(lastCandle)) {
                    trends.add(existingTrend);
                    continue;
                }
            }

            if(secondLastCandle.high() > lastCandle.high() && secondLastCandle.low() < lastCandle.low()) {
                existingTrend = Trend.CONSOLIDATION;
                consolidationStartIndex = i;
                trends.add(existingTrend);
                continue;
            }

            // consolidation logic ends

            if (isStronglyBullish(lastCandle) && isHigherHighAndHigherLow(secondLastCandle, lastCandle)) {
                existingTrend = Trend.UPTREND;
                trends.add(existingTrend);
                continue;
            }

            if (existingTrend == Trend.UPTREND && isHigherHighAndHigherLow(secondLastCandle, lastCandle)) {
                trends.add(existingTrend);
                continue;
            }

            if (isStronglyBearish(lastCandle) && isLowerHighAndLowerLow(secondLastCandle, lastCandle)) {
                existingTrend = Trend.DOWNTREND;
                trends.add(existingTrend);
                continue;
            }

            if (existingTrend == Trend.DOWNTREND && isLowerHighAndLowerLow(secondLastCandle, lastCandle)) {
                trends.add(existingTrend);
                continue;
            }

            if(isIndecisive(lastCandle)) {
                existingTrend = Trend.INDECISIVE;
                trends.add(existingTrend);
                continue;
            }


            existingTrend = Trend.INDECISIVE;
            trends.add(existingTrend);
        }
        return trends;
    }

    private static boolean isIndecisive(Candle candle) {
        /* for bearish*/
        return candle.low() != candle.close() /*for bullish*/
                && candle.high() != candle.close();
    }
    private static boolean isStronglyBullish(Candle candle) {
        return CandleUtils.isBullish(candle) && candle.low() == candle.open();
    }

    private static boolean isStronglyBearish(Candle candle) {
        return CandleUtils.isBearish(candle) && candle.high() == candle.open();
    }
    // CPD-ON
}
