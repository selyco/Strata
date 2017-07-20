/*
 * Copyright (C) 2017 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.loader.csv;

import static com.opengamma.strata.collect.Guavate.toImmutableList;
import static com.opengamma.strata.collect.TestHelper.coverPrivateConstructor;
import static com.opengamma.strata.collect.TestHelper.date;
import static com.opengamma.strata.product.common.BuySell.BUY;
import static com.opengamma.strata.product.common.BuySell.SELL;
import static org.joda.beans.test.BeanAssert.assertBeanEquals;
import static org.testng.Assert.assertEquals;

import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.util.List;

import org.testng.annotations.Test;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.StandardId;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.basics.index.IborIndices;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.collect.result.ValueWithFailures;
import com.opengamma.strata.product.Trade;
import com.opengamma.strata.product.TradeInfo;
import com.opengamma.strata.product.fra.Fra;
import com.opengamma.strata.product.fra.FraTrade;
import com.opengamma.strata.product.fra.type.FraConventions;
import com.opengamma.strata.product.swap.SwapTrade;
import com.opengamma.strata.product.swap.type.FixedIborSwapConventions;

/**
 * Test {@link TradeCsvLoader}.
 */
@Test
public class TradeCsvLoaderTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();

  private static final ResourceLocator FILE =
      ResourceLocator.of("classpath:com/opengamma/strata/loader/csv/trades.csv");

  //-------------------------------------------------------------------------
  public void test_load_failures() {
    TradeCsvLoader test = TradeCsvLoader.of();
    ValueWithFailures<List<Trade>> trades = test.load(FILE);

    assertEquals(trades.getFailures().size(), 0, trades.getFailures().toString());
  }

  //-------------------------------------------------------------------------
  public void test_load_fra() {
    TradeCsvLoader test = TradeCsvLoader.of();
    ValueWithFailures<List<Trade>> trades = test.load(FILE);

    List<FraTrade> fras = trades.getValue().stream()
        .filter(FraTrade.class::isInstance)
        .map(FraTrade.class::cast)
        .collect(toImmutableList());
    assertEquals(fras.size(), 3);

    FraTrade expected1 = FraConventions.of(IborIndices.GBP_LIBOR_3M)
        .createTrade(date(2017, 6, 1), Period.ofMonths(2), BUY, 1_000_000, 0.005, REF_DATA)
        .toBuilder()
        .info(TradeInfo.builder()
            .id(StandardId.of("OG", "123401"))
            .tradeDate(date(2017, 6, 1))
            .tradeTime(LocalTime.of(11, 5))
            .zone(ZoneId.of("Europe/London"))
            .build())
        .build();
    assertBeanEquals(fras.get(0), expected1);

    FraTrade expected2 = FraConventions.of(IborIndices.GBP_LIBOR_6M)
        .toTrade(date(2017, 6, 1), date(2017, 8, 1), date(2018, 2, 1), date(2017, 8, 1), SELL, 1_000_000, 0.007)
        .toBuilder()
        .info(TradeInfo.builder()
            .id(StandardId.of("OG", "123402"))
            .tradeDate(date(2017, 6, 1))
            .tradeTime(LocalTime.of(12, 35))
            .zone(ZoneId.of("Europe/London"))
            .build())
        .build();
    assertBeanEquals(fras.get(1), expected2);

    FraTrade expected3 = FraTrade.builder()
        .info(TradeInfo.builder()
            .id(StandardId.of("OG", "123403"))
            .tradeDate(date(2017, 6, 1))
            .build())
        .product(Fra.builder()
            .buySell(SELL)
            .startDate(date(2017, 8, 1))
            .endDate(date(2018, 1, 15))
            .notional(1_000_000)
            .fixedRate(0.0055)
            .index(IborIndices.GBP_LIBOR_3M)
            .indexInterpolated(IborIndices.GBP_LIBOR_6M)
            .build())
        .build();
    assertBeanEquals(fras.get(2), expected3);
  }

  //-------------------------------------------------------------------------
  public void test_load_swap() {
    TradeCsvLoader test = TradeCsvLoader.of();
    ValueWithFailures<List<Trade>> trades = test.load(FILE);

    List<SwapTrade> swaps = trades.getValue().stream()
        .filter(SwapTrade.class::isInstance)
        .map(SwapTrade.class::cast)
        .collect(toImmutableList());
    assertEquals(swaps.size(), 2);

    SwapTrade expected1 = FixedIborSwapConventions.GBP_FIXED_1Y_LIBOR_3M
        .createTrade(date(2017, 6, 1), Period.ofMonths(1), Tenor.ofYears(5), BUY, 2_000_000, 0.004, REF_DATA)
        .toBuilder()
        .info(TradeInfo.builder()
            .id(StandardId.of("OG", "123404"))
            .tradeDate(date(2017, 6, 1))
            .build())
        .build();
    assertBeanEquals(swaps.get(0), expected1);

    SwapTrade expected2 = FixedIborSwapConventions.GBP_FIXED_6M_LIBOR_6M
        .toTrade(date(2017, 6, 1), date(2017, 8, 1), date(2022, 8, 1), BUY, 3_100_000, -0.0001)
        .toBuilder()
        .info(TradeInfo.builder()
            .id(StandardId.of("OG", "123405"))
            .tradeDate(date(2017, 6, 1))
            .build())
        .build();
    assertBeanEquals(swaps.get(1), expected2);
  }

  //-------------------------------------------------------------------------
  public void coverage() {
    coverPrivateConstructor(FraTradeCsvLoader.class);
  }

}
