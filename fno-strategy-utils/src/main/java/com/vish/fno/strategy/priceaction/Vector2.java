package com.vish.fno.strategy.priceaction;

import java.util.Objects;

/**
 * Minimal 2D vector with public x/y fields.
 * Used as base class for Point in price action analysis.
 */
public class Vector2 {
    public float x;
    public float y;

    public Vector2(float x, float y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Vector2 v = (Vector2) o;
        return Float.compare(v.x, x) == 0 && Float.compare(v.y, y) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }
}
