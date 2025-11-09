package com.vish.fno.strategy.priceaction;

import com.vish.fno.strategy.PointType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Point extends Vector2 implements Comparable<Point> {
    private static final long serialVersionUID = 1L;
    private PointType type;


    public Point(float x, float y) {
        super(x, y);
    }

    public float getX() {
        return this.x;
    }

    public float getY() {
        return this.y;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + (int) super.x;
        result = prime * result + (int) super.y;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Point other = (Point) obj;
        return x == other.x && y == other.y;
    }

    @Override
    public int compareTo(Point that) {
        return (int) (this.x - that.x);
    }
}
