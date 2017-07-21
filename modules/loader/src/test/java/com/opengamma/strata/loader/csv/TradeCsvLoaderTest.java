/*
 * Copyright (C) 2017 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.loader.csv;

import static com.opengamma.strata.basics.currency.Currency.GBP;
import static com.opengamma.strata.basics.date.BusinessDayConventions.MODIFIED_FOLLOWING;
import static com.opengamma.strata.basics.date.HolidayCalendarIds.GBLO;
import static com.opengamma.strata.collect.Guavate.toImmutableList;
import static com.opengamma.strata.collect.TestHelper.coverPrivateConstructor;
import static com.opengamma.strata.collect.TestHelper.date;
import static com.opengamma.strata.product.common.BuySell.BUY;
import static com.opengamma.strata.product.common.BuySell.SELL;
import static com.opengamma.strata.product.common.PayReceive.PAY;
import static com.opengamma.strata.product.common.PayReceive.RECEIVE;
import static org.joda.beans.test.BeanAssert.assertBeanEquals;
import static org.testng.Assert.assertEquals;

import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.util.List;

import org.testng.annotations.Test;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.StandardId;
import com.opengamma.strata.basics.date.BusinessDayAdjustment;
import com.opengamma.strata.basics.date.DayCounts;
import com.opengamma.strata.basics.date.DaysAdjustment;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.basics.index.IborIndices;
import com.opengamma.strata.basics.schedule.Frequency;
import com.opengamma.strata.basics.schedule.PeriodicSchedule;
import com.opengamma.strata.basics.schedule.StubConvention;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.collect.result.ValueWithFailures;
import com.opengamma.strata.product.Trade;
import com.opengamma.strata.product.TradeInfo;
import com.opengamma.strata.product.deposit.TermDeposit;
import com.opengamma.strata.product.deposit.TermDepositTrade;
import com.opengamma.strata.product.deposit.type.TermDepositConventions;
import com.opengamma.strata.product.fra.Fra;
import com.opengamma.strata.product.fra.FraTrade;
import com.opengamma.strata.product.fra.type.FraConventions;
import com.opengamma.strata.product.swap.FixedRateCalculation;
import com.opengamma.strata.product.swap.IborRateCalculation;
import com.opengamma.strata.product.swap.NotionalSchedule;
import com.opengamma.strata.product.swap.PaymentSchedule;
import com.opengamma.strata.product.swap.RateCalculationSwapLeg;
import com.opengamma.strata.product.swap.Swap;
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

    List<FraTrade> filtered = trades.getValue().stream()
        .filter(FraTrade.class::isInstance)
        .map(FraTrade.class::cast)
        .collect(toImmutableList());
    assertEquals(filtered.size(), 3);

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
    assertBeanEquals(expected1, filtered.get(0));

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
    assertBeanEquals(expected2, filtered.get(1));

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
            .dayCount(DayCounts.ACT_360)
            .build())
        .build();
    assertBeanEquals(expected3, filtered.get(2));
  }

  //-------------------------------------------------------------------------
  public void test_load_swap() {
    TradeCsvLoader test = TradeCsvLoader.of();
    ValueWithFailures<List<Trade>> trades = test.load(FILE);

    List<SwapTrade> filtered = trades.getValue().stream()
        .filter(SwapTrade.class::isInstance)
        .map(SwapTrade.class::cast)
        .collect(toImmutableList());
    assertEquals(filtered.size(), 3);

    SwapTrade expected1 = FixedIborSwapConventions.GBP_FIXED_1Y_LIBOR_3M
        .createTrade(date(2017, 6, 1), Period.ofMonths(1), Tenor.ofYears(5), BUY, 2_000_000, 0.004, REF_DATA)
        .toBuilder()
        .info(TradeInfo.builder()
            .id(StandardId.of("OG", "123411"))
            .tradeDate(date(2017, 6, 1))
            .build())
        .build();
    assertBeanEquals(expected1, filtered.get(0));

    SwapTrade expected2 = FixedIborSwapConventions.GBP_FIXED_6M_LIBOR_6M
        .toTrade(date(2017, 6, 1), date(2017, 8, 1), date(2022, 8, 1), BUY, 3_100_000, -0.0001)
        .toBuilder()
        .info(TradeInfo.builder()
            .id(StandardId.of("OG", "123412"))
            .tradeDate(date(2017, 6, 1))
            .build())
        .build();
    assertBeanEquals(expected2, filtered.get(1));

    Swap expectedSwap3 = Swap.builder()
        .legs(
            RateCalculationSwapLeg.builder()
                .payReceive(PAY)
                .accrualSchedule(PeriodicSchedule.builder()
                    .startDate(date(2017, 8, 1))
                    .endDate(date(2022, 9, 1))
                    .frequency(Frequency.P6M)
                    .businessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, GBLO))
                    .stubConvention(StubConvention.LONG_FINAL)
                    .build())
                .paymentSchedule(PaymentSchedule.builder()
                    .paymentFrequency(Frequency.P6M)
                    .paymentDateOffset(DaysAdjustment.NONE)
                    .build())
                .notionalSchedule(NotionalSchedule.of(GBP, 4_000_000))
                .calculation(FixedRateCalculation.of(0.005, DayCounts.ACT_365F))
                .build(),
            RateCalculationSwapLeg.builder()
                .payReceive(RECEIVE)
                .accrualSchedule(PeriodicSchedule.builder()
                    .startDate(date(2017, 8, 1))
                    .endDate(date(2022, 9, 1))
                    .frequency(Frequency.P6M)
                    .businessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, GBLO))
                    .stubConvention(StubConvention.LONG_FINAL)
                    .build())
                .paymentSchedule(PaymentSchedule.builder()
                    .paymentFrequency(Frequency.P6M)
                    .paymentDateOffset(DaysAdjustment.NONE)
                    .build())
                .notionalSchedule(NotionalSchedule.of(GBP, 4_000_000))
                .calculation(IborRateCalculation.of(IborIndices.GBP_LIBOR_6M))
                .build())
        .build();
    SwapTrade expected3 = SwapTrade.builder()
        .info(TradeInfo.builder()
            .id(StandardId.of("OG", "123413"))
            .tradeDate(date(2017, 6, 1))
            .build())
        .product(expectedSwap3)
        .build();
    assertBeanEquals(expected3, filtered.get(2));
  }

  //-------------------------------------------------------------------------
  public void test_load_termDeposit() {
    TradeCsvLoader test = TradeCsvLoader.of();
    ValueWithFailures<List<Trade>> trades = test.load(FILE);

    List<TermDepositTrade> filtered = trades.getValue().stream()
        .filter(TermDepositTrade.class::isInstance)
        .map(TermDepositTrade.class::cast)
        .collect(toImmutableList());
    assertEquals(filtered.size(), 3);

    TermDepositTrade expected1 = TermDepositConventions.GBP_SHORT_DEPOSIT_T0
        .createTrade(date(2017, 6, 1), Period.ofWeeks(2), SELL, 400_000, 0.002, REF_DATA)
        .toBuilder()
        .info(TradeInfo.builder()
            .id(StandardId.of("OG", "123421"))
            .tradeDate(date(2017, 6, 1))
            .build())
        .build();
    assertBeanEquals(expected1, filtered.get(0));

    TermDepositTrade expected2 = TermDepositConventions.GBP_SHORT_DEPOSIT_T0
        .toTrade(date(2017, 6, 1), date(2017, 6, 1), date(2017, 6, 15), SELL, 500_000, 0.0022)
        .toBuilder()
        .info(TradeInfo.builder()
            .id(StandardId.of("OG", "123422"))
            .tradeDate(date(2017, 6, 1))
            .build())
        .build();
    assertBeanEquals(expected2, filtered.get(1));

    TermDepositTrade expected3 = TermDepositTrade.builder()
        .info(TradeInfo.builder()
            .id(StandardId.of("OG", "123423"))
            .tradeDate(date(2017, 6, 1))
            .build())
        .product(TermDeposit.builder()
            .buySell(BUY)
            .currency(GBP)
            .notional(600_000)
            .startDate(date(2017, 6, 1))
            .endDate(date(2017, 6, 22))
            .businessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, GBLO))
            .dayCount(DayCounts.ACT_365F)
            .rate(0.0023)
            .build())
        .build();
    assertBeanEquals(expected3, filtered.get(2));
  }

  //-------------------------------------------------------------------------
  public void coverage() {
    coverPrivateConstructor(FraTradeCsvLoader.class);
  }

}
