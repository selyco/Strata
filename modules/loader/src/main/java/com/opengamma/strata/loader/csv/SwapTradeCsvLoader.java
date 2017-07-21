/*
 * Copyright (C) 2017 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.loader.csv;

import static com.opengamma.strata.collect.Guavate.toImmutableList;
import static com.opengamma.strata.loader.csv.TradeCsvLoader.BUY_SELL_FIELD;
import static com.opengamma.strata.loader.csv.TradeCsvLoader.CONVENTION_FIELD;
import static com.opengamma.strata.loader.csv.TradeCsvLoader.END_DATE_FIELD;
import static com.opengamma.strata.loader.csv.TradeCsvLoader.FIXED_RATE_FIELD;
import static com.opengamma.strata.loader.csv.TradeCsvLoader.NOTIONAL_FIELD;
import static com.opengamma.strata.loader.csv.TradeCsvLoader.PERIOD_TO_START_FIELD;
import static com.opengamma.strata.loader.csv.TradeCsvLoader.START_DATE_FIELD;
import static com.opengamma.strata.loader.csv.TradeCsvLoader.TENOR_FIELD;
import static com.opengamma.strata.loader.csv.TradeCsvLoader.TRADE_DATE_FIELD;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.Optional;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.basics.schedule.PeriodicSchedule;
import com.opengamma.strata.basics.schedule.RollConvention;
import com.opengamma.strata.basics.schedule.StubConvention;
import com.opengamma.strata.collect.io.CsvRow;
import com.opengamma.strata.product.Trade;
import com.opengamma.strata.product.TradeInfo;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.swap.RateCalculationSwapLeg;
import com.opengamma.strata.product.swap.SwapLeg;
import com.opengamma.strata.product.swap.SwapTrade;
import com.opengamma.strata.product.swap.type.SingleCurrencySwapConvention;

/**
 * Loads Swap trades from CSV files.
 */
final class SwapTradeCsvLoader {

  // CSV column headers
  private static final String ROLL_CONVENTION_FIELD = "Roll Convention";
  private static final String STUB_CONVENTION_FIELD = "Stub Convention";
  private static final String FIRST_REGULAR_START_DATE_FIELD = "First Regular Start Date";
  private static final String LAST_REGULAR_END_DATE_FIELD = "Last Regular End Date";

  /**
   * Parses a FRA from the CSV row.
   * 
   * @param row  the CSV row
   * @param info  the trade info
   * @param refData  the reference data
   * @return the loaded trades, all errors are captured in the result
   */
  static Trade parse(CsvRow row, TradeInfo info, ReferenceData refData) {
    BuySell buySell = row.findValue(BUY_SELL_FIELD).map(s -> BuySell.of(s)).orElse(BuySell.BUY);
    String notionalStr = row.getValue(NOTIONAL_FIELD);
    double notional = new BigDecimal(notionalStr).doubleValue();
    String fixedRateStr = row.getValue(FIXED_RATE_FIELD);
    double fixedRate = new BigDecimal(fixedRateStr).divide(BigDecimal.valueOf(100)).doubleValue();
    Optional<SingleCurrencySwapConvention> conventionOpt =
        row.findValue(CONVENTION_FIELD).map(s -> SingleCurrencySwapConvention.of(s));
    Optional<Period> periodToStartOpt = row.findValue(PERIOD_TO_START_FIELD).map(s -> Tenor.parse(s).getPeriod());
    Optional<Tenor> tenorOpt = row.findValue(TENOR_FIELD).map(s -> Tenor.parse(s));
    Optional<LocalDate> startDateOpt = row.findValue(START_DATE_FIELD).map(s -> TradeCsvLoader.parseDate(s));
    Optional<LocalDate> endDateOpt = row.findValue(END_DATE_FIELD).map(s -> TradeCsvLoader.parseDate(s));
    Optional<RollConvention> rollConventionOpt = row.findValue(ROLL_CONVENTION_FIELD).map(s -> RollConvention.of(s));
    Optional<StubConvention> stubConventionOpt = row.findValue(STUB_CONVENTION_FIELD).map(s -> StubConvention.of(s));
    Optional<LocalDate> firstRegularStartDateOpt =
        row.findValue(FIRST_REGULAR_START_DATE_FIELD).map(s -> TradeCsvLoader.parseDate(s));
    Optional<LocalDate> lastRegEndDateOpt = row.findValue(LAST_REGULAR_END_DATE_FIELD).map(s -> TradeCsvLoader.parseDate(s));

    // use convention if available
    if (conventionOpt.isPresent()) {
      SingleCurrencySwapConvention convention = conventionOpt.get();
      // explicit dates take precedence over relative ones
      if (startDateOpt.isPresent() && endDateOpt.isPresent()) {
        if (periodToStartOpt.isPresent() || tenorOpt.isPresent()) {
          throw new IllegalArgumentException(
              "CSV file 'Swap' trade had invalid combination of fields. When these fields are found " +
                  ImmutableList.of(CONVENTION_FIELD, START_DATE_FIELD, END_DATE_FIELD) +
                  " then these fields must not be present " +
                  ImmutableList.of(PERIOD_TO_START_FIELD, TENOR_FIELD));
        }
        LocalDate startDate = startDateOpt.get();
        LocalDate endDate = endDateOpt.get();
        SwapTrade trade = convention.toTrade(info, startDate, endDate, buySell, notional, fixedRate);
        return adjustSchedule(trade, rollConventionOpt, stubConventionOpt, firstRegularStartDateOpt, lastRegEndDateOpt);
      }
      // relative dates
      if (periodToStartOpt.isPresent() && tenorOpt.isPresent() && info.getTradeDate().isPresent()) {
        if (startDateOpt.isPresent() || endDateOpt.isPresent()) {
          throw new IllegalArgumentException(
              "CSV file 'Swap' trade had invalid combination of fields. When these fields are found " +
                  ImmutableList.of(CONVENTION_FIELD, PERIOD_TO_START_FIELD, TENOR_FIELD, TRADE_DATE_FIELD) +
                  " then these fields must not be present " +
                  ImmutableList.of(START_DATE_FIELD, END_DATE_FIELD));
        }
        LocalDate tradeDate = info.getTradeDate().get();
        Period periodToStart = periodToStartOpt.get();
        Tenor tenor = tenorOpt.get();
        SwapTrade trade = convention.createTrade(tradeDate, periodToStart, tenor, buySell, notional, fixedRate, refData);
        trade = trade.toBuilder().info(info).build();
        return adjustSchedule(trade, rollConventionOpt, stubConventionOpt, firstRegularStartDateOpt, lastRegEndDateOpt);
      }
    }
    // no match
    throw new IllegalArgumentException(
        "CSV file 'Fra' trade had invalid combination of fields. These fields are mandatory:" +
            ImmutableList.of(BUY_SELL_FIELD, NOTIONAL_FIELD, FIXED_RATE_FIELD) +
            " and one of these combinations is mandatory: " +
            ImmutableList.of(CONVENTION_FIELD, TRADE_DATE_FIELD, PERIOD_TO_START_FIELD, TENOR_FIELD) +
            " or " +
            ImmutableList.of(CONVENTION_FIELD, START_DATE_FIELD, END_DATE_FIELD));
  }

  private static Trade adjustSchedule(
      SwapTrade trade,
      Optional<RollConvention> rollConventionOpt,
      Optional<StubConvention> stubConventionOpt,
      Optional<LocalDate> firstRegularStartDateOpt,
      Optional<LocalDate> lastRegEndDateOpt) {

    if (!rollConventionOpt.isPresent() &&
        !stubConventionOpt.isPresent() &&
        !firstRegularStartDateOpt.isPresent() &&
        !lastRegEndDateOpt.isPresent()) {
      return trade;
    }
    ImmutableList<SwapLeg> legs = trade.getProduct().getLegs().stream()
        .map(leg -> (RateCalculationSwapLeg) leg)
        .map(leg -> adjustLeg(leg, rollConventionOpt, stubConventionOpt, firstRegularStartDateOpt, lastRegEndDateOpt))
        .collect(toImmutableList());
    return trade.toBuilder()
        .product(trade.getProduct().toBuilder()
            .legs(legs)
            .build())
        .build();
  }

  private static SwapLeg adjustLeg(
      RateCalculationSwapLeg leg,
      Optional<RollConvention> rollConventionOpt,
      Optional<StubConvention> stubConventionOpt,
      Optional<LocalDate> firstRegularStartDateOpt,
      Optional<LocalDate> lastRegEndDateOpt) {

    PeriodicSchedule.Builder scheduleBuilder = leg.getAccrualSchedule().toBuilder();
    rollConventionOpt.ifPresent(rc -> scheduleBuilder.rollConvention(rc));
    stubConventionOpt.ifPresent(sc -> scheduleBuilder.stubConvention(sc));
    firstRegularStartDateOpt.ifPresent(date -> scheduleBuilder.firstRegularStartDate(date));
    lastRegEndDateOpt.ifPresent(date -> scheduleBuilder.lastRegularEndDate(date));
    return leg.toBuilder()
        .accrualSchedule(scheduleBuilder.build())
        .build();
  }

  //-------------------------------------------------------------------------
  // Restricted constructor.
  private SwapTradeCsvLoader() {
  }

}
