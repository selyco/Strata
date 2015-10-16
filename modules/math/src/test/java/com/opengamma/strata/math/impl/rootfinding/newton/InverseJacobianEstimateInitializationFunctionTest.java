/**
 * Copyright (C) 2009 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.strata.math.impl.rootfinding.newton;

import static org.testng.AssertJUnit.assertEquals;

import org.testng.annotations.Test;

import com.opengamma.strata.math.impl.function.Function1D;
import com.opengamma.strata.math.impl.linearalgebra.Decomposition;
import com.opengamma.strata.math.impl.linearalgebra.DecompositionFactory;
import com.opengamma.strata.math.impl.matrix.CommonsMatrixAlgebra;
import com.opengamma.strata.math.impl.matrix.DoubleArray;
import com.opengamma.strata.math.impl.matrix.DoubleMatrix;
import com.opengamma.strata.math.impl.matrix.DoubleMatrixUtils;
import com.opengamma.strata.math.impl.matrix.MatrixAlgebra;

/**
 * Test.
 */
@Test
public class InverseJacobianEstimateInitializationFunctionTest {

  private static final MatrixAlgebra ALGEBRA = new CommonsMatrixAlgebra();
  private static final Decomposition<?> SV = DecompositionFactory.SV_COMMONS;
  private static final InverseJacobianEstimateInitializationFunction ESTIMATE = new InverseJacobianEstimateInitializationFunction(SV);
  private static final Function1D<DoubleArray, DoubleMatrix> J = new Function1D<DoubleArray, DoubleMatrix>() {

    @Override
    public DoubleMatrix evaluate(DoubleArray v) {
      double[] x = v.toArray();
      return DoubleMatrix.copyOf(new double[][] { {x[0] * x[0], x[0] * x[1]}, {x[0] - x[1], x[1] * x[1]}});
    }

  };
  private static final DoubleArray X = DoubleArray.of(3, 4);

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testNullDecomposition() {
    new InverseJacobianEstimateInitializationFunction(null);
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testNullFunction() {
    ESTIMATE.getInitializedMatrix(null, X);
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testNullVector() {
    ESTIMATE.getInitializedMatrix(J, null);
  }

  public void test() {
    DoubleMatrix m1 = ESTIMATE.getInitializedMatrix(J, X);
    DoubleMatrix m2 = J.evaluate(X);
    DoubleMatrix m3 = (DoubleMatrix) (ALGEBRA.multiply(m1, m2));
    DoubleMatrix identity = DoubleMatrixUtils.getIdentityMatrix2D(2);
    for (int i = 0; i < 2; i++) {
      for (int j = 0; j < 2; j++) {
        assertEquals(m3.get(i, j), identity.get(i, j), 1e-6);
      }
    }
  }

}