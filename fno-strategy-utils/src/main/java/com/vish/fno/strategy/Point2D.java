package com.vish.fno.strategy;
import com.vish.fno.util.TimeUtils;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@Data
public class Point2D implements Comparable<Point2D> {
    int x;
    double y;
    PointType type;

    public Point2D(int x, double y) {
        this.x = x;
        this.y = y;
    }

    public Point2D(int x, double y, PointType type) {
        this.x = x;
        this.y = y;
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()){
            return false;
        }
        Point2D point2D = (Point2D) o;
        return x == point2D.x && Double.compare(point2D.y, y) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public int compareTo(@NotNull Point2D o) {
        return Integer.compare(this.x, o.x);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Point2D{");
        sb.append("x=")
                .append(TimeUtils.timeArray.get(x))
                .append(", y=")
                .append(y)
                .append(", type=")
                .append(type)
                .append('}');
        return sb.toString();
    }
}