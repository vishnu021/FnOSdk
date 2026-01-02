package com.vish.fno.strategy.priceaction;

import com.vish.fno.model.Candle;
import com.vish.fno.strategy.Point2D;
import com.vish.fno.strategy.PointType;
import com.vish.fno.util.CandleUtils;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

// https://www.geeksforgeeks.org/find-indices-of-all-local-maxima-and-local-minima-in-an-array/
// https://stackoverflow.com/questions/4557840/find-all-collinear-points-in-a-given-set
@Slf4j
@SuppressWarnings("PMD.UnusedPrivateMethod")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DataAnalyser {

    private static final int MIN_LINE_LENGTH = 40;
    private static final int ACTIVE_LINE_HIT_CORRECTION = 30;

    // TODO: merge/omit very close minima-maxima points
    public static List<Point2D> getMaximaMinimaPoints(final List<Candle> candles, final int range) {
        final List<Point2D> minimaPoints = calculateMinimaPoints(candles, range).stream().sorted().toList();
        final List<Point2D> maximaPoints = calculateMaximaPoints(candles, range).stream().sorted().toList();
//        log.info("MinimaPoints : {}", minimaPoints.stream().map(p -> TimeUtils.getTimeByIndex(p.getX() * 5) + " -> " + p.getY()).toList());
//        log.info("MaximaPoints : {}", maximaPoints.stream().map(p -> TimeUtils.getTimeByIndex(p.getX() * 5) + " -> " + p.getY()).toList());
        return mergeMinimaMaxima(minimaPoints, maximaPoints);
    }

    private static List<Point2D> mergeMinimaMaxima(final List<Point2D> minimaPoints, final List<Point2D> maximaPoints) {
        if(minimaPoints.isEmpty() || maximaPoints.isEmpty()){
            return List.of();
        }

        final Map<Integer, Point2D> minimaPointsMap = translateToMap(minimaPoints);
        final Map<Integer, Point2D> maximaPointsMap = translateToMap(maximaPoints);

        Set<Integer> timeIndices = getAllTimeIndices(minimaPointsMap, maximaPointsMap);
        Set<Point2D> mergedPoints = new HashSet<>();

        boolean isMinimaPointPassed = false;
        boolean isMaximaPointPassed = false;

        List<Point2D> localMinimaPoints = new ArrayList<>();
        List<Point2D> localMaximaPoints = new ArrayList<>();

        for(int i : timeIndices) {
            if(minimaPointsMap.containsKey(i)) {
                if(isMaximaPointPassed) {
                    Point2D highestYPoint = Collections.max(localMaximaPoints, Comparator.comparing(Point2D::getY));
                    highestYPoint.setType(PointType.MAXIMA);
                    mergedPoints.add(highestYPoint);
                    localMaximaPoints.clear();
                }
                localMinimaPoints.add(minimaPointsMap.get(i));
                isMinimaPointPassed = true;
                isMaximaPointPassed = false;
            }
            if(maximaPointsMap.containsKey(i)) {
                if(isMinimaPointPassed) {
                    Point2D lowestYPoint = Collections.min(localMinimaPoints, Comparator.comparing(Point2D::getY));
                    lowestYPoint.setType(PointType.MINIMA);
//                    if(mergedPoints.contains(lowestYPoint)) {
//                        lowestYPoint.setType(PointType.BOTH);
//                    }
                    mergedPoints.add(lowestYPoint);
                    localMinimaPoints.clear();
                }
                localMaximaPoints.add(maximaPointsMap.get(i));
                isMaximaPointPassed = true;
                isMinimaPointPassed = false;
            }
        }

        return mergedPoints.stream().sorted().toList();
    }

    @NotNull
    private static Set<Integer> getAllTimeIndices(final Map<Integer, Point2D> minimaPointsMap,
                                                      final Map<Integer, Point2D> maximaPointsMap) {
        Set<Integer> timeIndices = new TreeSet<>();
        timeIndices.addAll(minimaPointsMap.keySet());
        timeIndices.addAll(maximaPointsMap.keySet());
        return timeIndices;
    }

    @NotNull
    private static Map<Integer, Point2D> translateToMap(List<Point2D> minimaPoints) {
        return minimaPoints.stream()
                .collect(Collectors.toMap(
                        Point2D::getX,
                        point -> point,
                        (existing, replacement) -> {
                            log.warn("Duplicate value for same key, value: {}", existing);
                            return existing;
                        },
                        TreeMap::new
                ));
    }

    public static void joinLongCollinearPoints(Set<Line> collinearPoints) {
        List<Line> collinearPointList = new ArrayList<>(collinearPoints);
        List<Line> redundantPoints = new ArrayList<>();

        for (int i = 0; i < collinearPointList.size(); i++) {
            for (int j = i + 1; j < collinearPointList.size(); j++) {
                if (collinearPointList.get(i).isSameLineSegment(collinearPointList.get(j))) {
                    collinearPointList.get(i).getPoints().addAll(collinearPointList.get(j).getPoints());
                    if (!redundantPoints.contains(collinearPointList.get(i))) {
                        redundantPoints.add(collinearPointList.get(j));
                    }
                }
            }
        }
        collinearPoints.removeAll(redundantPoints);
    }

    private static Set<Integer> calculateMaximaMaximaIndex(double[] arr, int range) {
        Set<Integer> maximaPoints = new HashSet<>();
        int n = arr.length;

        if (n < range || range < 1) {
            return maximaPoints;
        }

        for (int i = 0; i < n; i++) {
            // Adjust the start and end indices based on the range and current index
            int start = Math.max(i - range / 2, 0);
            int end = Math.min(i + range / 2, n - 1);

            // Get the minimum value in the specified range
            double minValue = arr[start];
            for (int j = start + 1; j <= end; j++) {
                minValue = Math.min(minValue, arr[j]);
            }

            // Check if the current element is the local minima
            if (arr[i] == minValue) {
                maximaPoints.add(i);
            }
        }
        return maximaPoints;
    }

    public static Set<Point2D> calculateMinimaPoints(List<Candle> candles, int range) {
        Set<Point2D> minimaPoints = new HashSet<>();

        if (candles.size() < range) {
            return minimaPoints;
        }

        for(int i = 0; i < range; i++) {
            if(candles.get(0).low() <= getMinimum(candles.subList(0, range/2 + 1))) {
                minimaPoints.add(new Point2D(0, candles.get(0).low()));
            }
        }

        for(int i = range/2; i < candles.size() - range/2; i++) {
            if(candles.get(i).low() <= getMinimum(candles.subList(i - range/2, i + range/2 + 1))) {
                minimaPoints.add(new Point2D(i, candles.get(i).low()));
            }
        }
        return minimaPoints;
    }

    public static Set<Point2D> calculateMaximaPoints(List<Candle> candles, int range) {
        Set<Point2D> maximaPoints = new HashSet<>();

        if (candles.size() < range) {
            return maximaPoints;
        }

        for(int i = 0; i<range; i++) {
            if(candles.get(0).high() >= getMaximum(candles.subList(0, range/2 + 1))) {
                maximaPoints.add(new Point2D(0, candles.get(0).high()));
            }
        }

        for(int i = range/2; i< candles.size() - range/2; i++) {
            if(candles.get(i).high() >= getMaximum(candles.subList(i - range/2, i + range/2 + 1))) {
                maximaPoints.add(new Point2D(i, candles.get(i).high())); // TODO, if 2 adjacent candles have exactly same high
            }
        }
        return maximaPoints;
    }

    private static double getMinimum(List<Candle> candles) {
        return candles.stream().mapToDouble(Candle::low).min().getAsDouble();
    }
    private static double getMaximum(List<Candle> candles) {
        return candles.stream().mapToDouble(Candle::high).max().getAsDouble();
    }



    private static double getMaximum(double... values) {
        return Arrays.stream(values).max().getAsDouble();
    }

    private static double getMinimum(double... values) {
        return Arrays.stream(values).min().getAsDouble();
    }


    public static SortedSet<Line> getActiveLines(List<Candle> candles, Set<Line> lines, boolean isMaxima) {
        SortedSet<Line> activeLines = new TreeSet<>();
//        List<Candle> candles = dataModel.getCandleData();

        for (Line line : lines) {
            Point firstPoint = line.getFirstPoint();
            Candle lastCandle = candles.get(candles.size() - 1);
            if (isLineIntersections(candles, line, (int) firstPoint.x, candles.size(), isMaxima)) {
                // not active line
                Point hitPoint = getCandleHitPoint(candles, line, (int) firstPoint.x, candles.size(), isMaxima);

                if (hitPoint.x + ACTIVE_LINE_HIT_CORRECTION > candles.size()) {
                    activeLines.add(line);
                    line.setHitPoint(hitPoint);
                    line.setActiveLine(true);
                } else {
                    line.setHitPoint(hitPoint);
                    line.setActiveLine(false);
                }
            }

            else if (lastCandle.high() * 1.33 < line.getY(candles.size())) {
                line.setActiveLine(false);
            }

            else {
                activeLines.add(line);
                line.setActiveLine(true);
            }

        }
        return activeLines;
    }

    public static Set<Line> findCollinearPoints(Set<Point> pointsSet, int startIndex) {
        List<Point> points = new ArrayList<>(pointsSet);

        Set<Line> collinearPoints = new HashSet<>();

        for (int i = 0; i < points.size(); i++) {
            for (int j = i + 1; j < points.size(); j++) {
                for (int k = startIndex; k < points.size(); k++) {
                    if (k == i || k == j) {
                        continue;
                    }

                    if (isCollinear(points.get(i), points.get(j), points.get(k))) {
                        Line line = new Line(points.get(i), points.get(j), points.get(k));
                        collinearPoints.add(line);
                    }
                }
            }
        }

        return collinearPoints;
    }

    private static boolean isCollinear(Vector2 p1, Vector2 p2, Vector2 p3) {
        double distance1 = distance(p1, p2);
        double distance2 = distance(p3, p2);
        double distance3 = distance(p1, p3);
        double perimeter = (distance1 + distance2 + distance3);
        double longestSide = Math.max(distance1, Math.max(distance2, distance3));
        double yRange = Math.max(p1.y, Math.max(p2.y, p3.y)) - Math.min(p1.y, Math.min(p2.y, p3.y));
        double delta = ((perimeter - 2 * longestSide) * 10000) / (perimeter * yRange);
        return Math.abs(delta) < 0.0004;
//		return Math.abs(delta) < 0.00004;
    }

    static double distance(Vector2 p, Vector2 q) {
        return Math.pow((p.x - q.x) * (p.x - q.x) + (p.y - q.y) * (p.y - q.y), 0.5);
    }

//    public static Set<Point> calculateMaximaPoints(List<Candle> candleData, int startIndex) {
//        Set<Point> maximaPoints = new HashSet<>();
//        double[] arr = candleData.stream().map(Candle::high).mapToDouble(i -> i).toArray();
//        int n = arr.length;
//
//        if (n < 2 || startIndex < 0)
//            return maximaPoints;
//
//        // Checking whether the first point is local maxima
//        if (arr[0] > arr[1] && startIndex == 0)
//            maximaPoints.add(new Point(0, (float) arr[0]));
//
//        // Iterating over all points to check local maxima
//        for (int i = startIndex + 1; i < n - 1; i++) {
//            // Condition for local maxima
//            if ((arr[i - 1] < arr[i]) && (arr[i] > arr[i + 1]))
//                maximaPoints.add(new Point(i, (float) arr[i]));
//        }
//
//        return maximaPoints;
//    }

//    public static Set<Point> calculateMinimaPoints(List<Candle> candleData, int startIndex) {
//        Set<Point> minimaPoints = new HashSet<>();
//        double[] arr = candleData.stream().map(Candle::low).mapToDouble(i -> i).toArray();
//        int n = arr.length;
//
//        if (n < 2)
//            return minimaPoints;
//
//        // Checking whether the first point is local minima
//        if (arr[0] < arr[1] && startIndex == 0)
//            minimaPoints.add(new Point(0, (float) arr[0]));
//        // Iterating over all points to check local maxima and local minima
//        for (int i = startIndex + 1; i < n - 1; i++) {
//            // Condition for local minima
//            if ((arr[i - 1] > arr[i]) && (arr[i] < arr[i + 1]))
//                minimaPoints.add(new Point(i, (float) arr[i]));
//        }
//        return minimaPoints;
//    }

//    public static Set<Point> calculateMinimaPoints(List<Point> points) {
//        Set<Point> minimaPoints = new HashSet<>();
//        double[] arr = points.stream().map(Point::getY).mapToDouble(i -> i).toArray();
//        int n = arr.length;
//
//        if (n < 2)
//            return minimaPoints;
//
//        int startIndex = 0;
//
//        // Checking whether the first point is local minima
//        if (arr[0] < arr[1] && startIndex == 0)
//            minimaPoints.add(new Point(0, (float) arr[0]));
//        // Iterating over all points to check local maxima and local minima
//        for (int i = startIndex + 1; i < n - 1; i++) {
//            // Condition for local minima
//            if ((arr[i - 1] > arr[i]) && (arr[i] < arr[i + 1]))
//                minimaPoints.add(new Point(points.get(i).getX(), (float) arr[i]));
//        }
//        return minimaPoints;
//    }

    public static void removePointCoincidingWithLines(List<Candle> candleData, Set<Line> points, boolean isMaxima) {
        List<Line> coincidingLines = new ArrayList<>();
        for (Line point : points) {
            if (doesLineHitsCandleBody(candleData, point, isMaxima)) {
                coincidingLines.add(point);
            }

        }
        points.removeAll(coincidingLines);
    }

    private static boolean doesLineHitsCandleBody(List<Candle> list, Line collinearPoint, boolean isMaxima) {
        int minX = (int) (collinearPoint.getFirstPoint().getX() + 1);
        int maxX = (int) (collinearPoint.getLastPoint().getX() + 1);

        return isLineIntersections(list, collinearPoint, minX, maxX, isMaxima);
    }

    private static boolean isLineIntersections(List<Candle> candles, Line line, int minX, int maxX,
                                               boolean isMaxima) {
        for (int i = minX + 1; i < maxX; i++) {
            Candle candle = candles.get(i);
            float y = line.getY(i);
            if (containsPoint(candle, y, isMaxima)) {
                return true;
            }
        }
        return false;
    }

    private static Point getCandleHitPoint(List<Candle> candles, Line line, int minX, int maxX, boolean isMaxima) {
        for (int i = minX + 1; i < maxX; i++) {
            Candle candle = candles.get(i);
            float y = line.getY(i);
            if (containsPoint(candle, y, isMaxima)) {
                return new Point(i, y);
            }
        }
        return null;
    }

    public static boolean containsPoint(Candle c, float y, boolean isMaxima) {
        // bull candle
        if (CandleUtils.isBullish(c)) {
            if (isMaxima) {
                return y < c.close();
            }
            else {
                return y > c.open();
            }
        }
        // bear candle
        if (isMaxima) {
            return y < c.open();
        }
        else {
            return y > c.close();
        }
    }

    public static void removeShortLines(Set<Line> lines) {
        Set<Line> shortLines = new HashSet<>();
        for (Line line : lines) {
            if (line.getXAxisLength() < MIN_LINE_LENGTH) {
                shortLines.add(line);
            }
        }
        lines.removeAll(shortLines);
    }

    public static void removeTooClosePoints(Set<Line> lines) {
        Set<Line> shortLines = new HashSet<>();
        for (Line line : lines) {
            if (line.isPointTooClose()) {
                shortLines.add(line);
            }
        }
        lines.removeAll(shortLines);
    }
}
