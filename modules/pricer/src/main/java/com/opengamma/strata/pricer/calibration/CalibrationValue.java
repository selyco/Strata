/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.calibration;

import java.util.List;

import com.opengamma.strata.finance.Trade;
import com.opengamma.strata.math.impl.function.Function1D;
import com.opengamma.strata.math.impl.matrix.DoubleArray;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;

/**
 * Provides the calibration value.
 * <p>
 * This provides the value from the specified {@link CalibrationMeasures} instance
 * in matrix form suitable for use in curve calibration root finding.
 * The value will typically be par spread or converted present value.
 */
class CalibrationValue
    extends Function1D<DoubleArray, DoubleArray> {

  /**
   * The trades.
   */
  private final List<Trade> trades;
  /**
   * The calibration measures.
   */
  private final CalibrationMeasures measures;
  /**
   * The provider generator, used to create child providers.
   */
  private final RatesProviderGenerator providerGenerator;

  /**
   * Creates an instance.
   * 
   * @param trades  the trades
   * @param measures  the calibration measures
   * @param providerGenerator  the provider generator, used to create child providers
   */
  CalibrationValue(
      List<Trade> trades,
      CalibrationMeasures measures,
      RatesProviderGenerator providerGenerator) {

    this.trades = trades;
    this.measures = measures;
    this.providerGenerator = providerGenerator;
  }

  //-------------------------------------------------------------------------
  @Override
  public DoubleArray evaluate(DoubleArray x) {
    // create child provider from matrix
    ImmutableRatesProvider childProvider = providerGenerator.generate(x);
    // calculate value for each trade using the child provider
    return DoubleArray.of(trades.size(), i -> measures.value(trades.get(i), childProvider));
  }

}