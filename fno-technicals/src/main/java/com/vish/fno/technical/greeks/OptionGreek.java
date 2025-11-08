package com.vish.fno.technical.greeks;

import org.apache.commons.math3.distribution.NormalDistribution;

@SuppressWarnings("PMD.AbstractClassWithoutAbstractMethod")
public abstract class OptionGreek {

    private static final NormalDistribution N = new NormalDistribution();

    protected static double getDensity(double d1) {
        return N.density(d1);
    }

    protected static double cumulativeProbability(double d1) {
        return N.cumulativeProbability(d1);
    }

    protected static double calculateD1(double S, double K, double T, double r, double sigma) {
        return (Math.log(S / K) + (r + Math.pow(sigma, 2) / 2) * T) / (sigma * Math.sqrt(T));
    }
}
