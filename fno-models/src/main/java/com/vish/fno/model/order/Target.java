package com.vish.fno.model.order;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Immutable value class wrapping one or more target prices for an order.
 * Uses {@link List} internally for standard collection interop and simplicity.
 *
 * <p>Single-target orders (the common case) use {@code Target.of(24550.0)}.
 * Multi-target orders use {@code Target.of(24550.0, 24600.0, 24650.0)}.
 */
public final class Target {
    private final List<Double> values;

    private Target(List<Double> values) {
        this.values = values;
    }

    public static Target of(double... targets) {
        return new Target(Arrays.stream(targets).boxed().toList());
    }

    public static Target of(List<Double> targets) {
        return new Target(List.copyOf(targets));
    }

    public double first() {
        return values.get(0);
    }

    public double get(int index) {
        return values.get(index);
    }

    public int size() {
        return values.size();
    }

    public boolean isMultiTarget() {
        return values.size() > 1;
    }

    public List<Double> asList() {
        return values;
    }

    @Override
    public String toString() {
        return values.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return Objects.equals(values, ((Target) o).values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }
}
