package com.droneanalytics.analytics.math;

/**
 * Fits a polynomial of degree n to a set of (x, y) data points using
 * ordinary least squares (OLS) via the normal equations.
 *
 * The system solved is:  (XᵀX) β = Xᵀy
 * where X is the Vandermonde matrix and β are the polynomial coefficients.
 *
 * This is used by the battery discharge model to fit:
 *   battPct(t) = a·t² + b·t + c
 *
 * Implementation note: for numerically stable results with time values
 * in the hundreds (seconds), callers should normalise t to [0, 1] before
 * fitting and then scale the coefficients back.
 */
public class PolynomialRegression {

    private final double[] coefficients; // [a_n, a_{n-1}, ..., a_1, a_0] highest degree first
    private final int      degree;
    private final double   rSquared;

    /**
     * Fits a polynomial of the given degree to the supplied data points.
     *
     * @param x      Independent variable array (e.g. time in seconds)
     * @param y      Dependent variable array (e.g. battery %)
     * @param degree Polynomial degree (1 = linear, 2 = quadratic, 3 = cubic)
     */
    public PolynomialRegression(double[] x, double[] y, int degree) {
        if (x.length != y.length) throw new IllegalArgumentException("x and y must have equal length");
        if (x.length <= degree)   throw new IllegalArgumentException("Need more data points than degree");

        this.degree = degree;
        int n = x.length;
        int d = degree + 1; // number of coefficients

        // Build Vandermonde matrix X (n × d)
        double[][] X = new double[n][d];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < d; j++) {
                X[i][j] = Math.pow(x[i], degree - j);
            }
        }

        // Compute XᵀX (d × d) and Xᵀy (d × 1)
        double[][] XtX = new double[d][d];
        double[]   Xty = new double[d];
        for (int i = 0; i < d; i++) {
            for (int k = 0; k < n; k++) {
                Xty[i] += X[k][i] * y[k];
                for (int j = 0; j < d; j++) {
                    XtX[i][j] += X[k][i] * X[k][j];
                }
            }
        }

        // Solve (XᵀX)β = Xᵀy via Gaussian elimination with partial pivoting
        this.coefficients = gaussianElimination(XtX, Xty);

        // Compute R²
        double yMean = 0;
        for (double v : y) yMean += v;
        yMean /= n;

        double ssTot = 0, ssRes = 0;
        for (int i = 0; i < n; i++) {
            double predicted = evaluate(x[i]);
            ssRes += (y[i] - predicted) * (y[i] - predicted);
            ssTot += (y[i] - yMean)     * (y[i] - yMean);
        }
        this.rSquared = (ssTot == 0) ? 1.0 : 1.0 - ssRes / ssTot;
    }

    /**
     * Evaluates the fitted polynomial at a given x value.
     * Returns  a_n·xⁿ + a_{n-1}·x^{n-1} + … + a_0
     */
    public double evaluate(double x) {
        double result = 0;
        for (int i = 0; i <= degree; i++) {
            result += coefficients[i] * Math.pow(x, degree - i);
        }
        return result;
    }

    /** Returns polynomial coefficients, highest degree first: [a_n, …, a_0]. */
    public double[] getCoefficients() { return coefficients.clone(); }

    /** R² goodness-of-fit statistic (0–1, 1 = perfect fit). */
    public double getRSquared() { return rSquared; }

    public int getDegree() { return degree; }

    // -------------------------------------------------------------------------
    // Gaussian elimination with partial pivoting
    // Solves A·x = b in place; returns the solution vector x.
    // -------------------------------------------------------------------------

    private static double[] gaussianElimination(double[][] A, double[] b) {
        int n = b.length;
        // Augmented matrix [A | b]
        double[][] aug = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(A[i], 0, aug[i], 0, n);
            aug[i][n] = b[i];
        }

        // Forward elimination
        for (int col = 0; col < n; col++) {
            // Find pivot
            int maxRow = col;
            for (int row = col + 1; row < n; row++) {
                if (Math.abs(aug[row][col]) > Math.abs(aug[maxRow][col])) {
                    maxRow = row;
                }
            }
            double[] tmp = aug[col]; aug[col] = aug[maxRow]; aug[maxRow] = tmp;

            if (Math.abs(aug[col][col]) < 1e-12) continue; // singular column

            for (int row = col + 1; row < n; row++) {
                double factor = aug[row][col] / aug[col][col];
                for (int j = col; j <= n; j++) {
                    aug[row][j] -= factor * aug[col][j];
                }
            }
        }

        // Back substitution
        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            if (Math.abs(aug[i][i]) < 1e-12) { x[i] = 0; continue; }
            x[i] = aug[i][n];
            for (int j = i + 1; j < n; j++) {
                x[i] -= aug[i][j] * x[j];
            }
            x[i] /= aug[i][i];
        }
        return x;
    }
}
