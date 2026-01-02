package com.vish.fno.strategy.priceaction;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

public class Line implements Comparable<Line> {
    private static final int MIN_DISTANCE_BETWEEN_POINTS = 9;
    @Getter
    private final SortedSet<Point> points;
    @Getter
    @Setter
    private Point hitPoint;
    @Getter
    @Setter
    private Point activeExtension;
    @Getter
    @Setter
    private boolean activeLine;
    private float m;
    private float c;
    private int pointsCount;

    public float getSlope() {
        return (float) (Math.atan(m) * 180 / Math.PI);
    }

    public Line(Point... args) {
        points = new TreeSet<>();
        Collections.addAll(points, args);
    }

    public float getXAxisLength() {
        return getLastPoint().getX() - getFirstPoint().getX();
    }

    // only valid for 3 points case
    public boolean isPointTooClose() {
        List<Point> closePoints = new ArrayList<>();

        Iterator<Point> iterator = points.iterator();
        Point previousPoint = iterator.next();
        Point currentPoint = iterator.next();

        while (iterator.hasNext()) {
            if (getXAxisDistance(previousPoint, currentPoint) < MIN_DISTANCE_BETWEEN_POINTS) {
                closePoints.add(currentPoint);
            } else {
                previousPoint = currentPoint;
            }
            currentPoint = iterator.next();
        }

        if (getXAxisDistance(previousPoint, currentPoint) < MIN_DISTANCE_BETWEEN_POINTS) {
            closePoints.add(currentPoint);
        }
        return (points.size() - closePoints.size()) < 3;
    }

    private int getXAxisDistance(Point point1, Point point2) {
        return (int) Math.abs(point1.x - point2.x);
    }

    public float getY(float x) {
        if (pointsCount != points.size()) {
            calculateSlopeAndConstant();
            pointsCount = points.size();
        }
        return m * x + c;
    }

    public void calculateSlopeAndConstant() {
        Point point1 = getFirstPoint();
        Point point2 = getLastPoint();
        this.m = (point2.y - point1.y) / (point2.x - point1.x);
        this.c = point1.y - m * point1.x;
    }

    public Point getFirstPoint() {
        return points.stream().findFirst().get();
    }

    public Point getLastPoint() {
        return points.stream().skip(points.size() - 1).findFirst().get();
    }

    public boolean isSameLineSegment(Line that) {
        List<Point> thatCopy = new ArrayList<>(that.getPoints());
        thatCopy.retainAll(this.points);
        return thatCopy.size() > 1;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((points == null) ? 0 : points.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Line other = (Line) obj;
        if (points == null) {
            return other.points == null;
        } else {
            return points.equals(other.points);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Points (");
        for (Point point : points) {
            sb.append((int) point.x).append(",");
        }

        sb.deleteCharAt(sb.length() - 1);
        sb.append(")");
        return sb.toString();
    }

    @Override
    public int compareTo(Line that) {
        return (int) (points.first().getX() - that.points.first().getX());
    }
}
