package com.trading.manualorderservice.util;

public class BlackScholesUtil {

    private static final double RISK_FREE_RATE = 0.065; // 6.5% yearly

    // --------------------------
    //   CDF for normal dist
    // --------------------------
    private static double phi(double x) {
        return 0.5 * (1.0 + erf(x / Math.sqrt(2.0)));
    }

    private static double erf(double x) {
        // Abramowitz & Stegun approximation
        double t = 1.0 / (1.0 + 0.5 * Math.abs(x));
        double tau = t * Math.exp(
                -x * x
                        - 1.26551223
                        + 1.00002368 * t
                        + 0.37409196 * t * t
                        + 0.09678418 * t * t * t
                        - 0.18628806 * Math.pow(t, 4)
                        + 0.27886807 * Math.pow(t, 5)
                        - 1.13520398 * Math.pow(t, 6)
                        + 1.48851587 * Math.pow(t, 7)
                        - 0.82215223 * Math.pow(t, 8)
                        + 0.17087277 * Math.pow(t, 9)
        );
        return (x >= 0) ? 1 - tau : tau - 1;
    }

    // --------------------------
    //   Black–Scholes price
    // --------------------------
    public static double optionPrice(boolean isCall, double S, double K, double T, double sigma) {
        double d1 = (Math.log(S / K) + (RISK_FREE_RATE + 0.5 * sigma * sigma) * T) / (sigma * Math.sqrt(T));
        double d2 = d1 - sigma * Math.sqrt(T);

        if (isCall) {
            return S * phi(d1) - K * Math.exp(-RISK_FREE_RATE * T) * phi(d2);
        } else {
            return K * Math.exp(-RISK_FREE_RATE * T) * phi(-d2) - S * phi(-d1);
        }
    }

    // --------------------------
    //   Compute IV (Newton method)
    // --------------------------
    public static double computeIV(boolean isCall, double S, double K, double LTP, double T) {
        double sigma = 0.25; // initial guess 25%
        for (int i = 0; i < 60; i++) {

            double price = optionPrice(isCall, S, K, T, sigma);
            double vega = vega(S, K, T, sigma);

            if (vega == 0) break;

            double diff = price - LTP;
            sigma -= diff / vega;

            if (Math.abs(diff) < 0.0001) break;
        }

        return Math.max(0.01, Math.min(sigma, 5)); // clamp 1% to 500%
    }

    private static double vega(double S, double K, double T, double sigma) {
        double d1 = (Math.log(S / K) + (RISK_FREE_RATE + 0.5 * sigma * sigma) * T) / (sigma * Math.sqrt(T));
        return S * Math.sqrt(T) * (1 / Math.sqrt(2 * Math.PI)) * Math.exp(-0.5 * d1 * d1);
    }
}
