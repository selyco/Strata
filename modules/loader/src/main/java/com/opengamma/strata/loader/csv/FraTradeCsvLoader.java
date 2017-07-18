/*
 * Copyright (C) 2017 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.loader.csv;

import static com.opengamma.strata.loader.csv.TradeCsvLoader.TRADE_DATE_FIELD;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.Optional;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.date.DayCount;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.collect.io.CsvRow;
import com.opengamma.strata.product.Trade;
import com.opengamma.strata.product.TradeInfo;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.fra.Fra;
import com.opengamma.strata.product.fra.FraTrade;
import com.opengamma.strata.product.fra.type.FraConvention;

/**
 * Loads FRA trades from CSV files.
 */
final class FraTradeCsvLoader {

  // CSV column headers
  private static final String CONVENTION_FIELD = "Convention";
  private static final String BUY_SELL_FIELD = "Buy Sell";
  private static final String CURRENCY_FIELD = "Currency";
  private static final String NOTIONAL_FIELD = "Notional";
  private static final String INDEX_FIELD = "Index";
  private static final String INTERPOLATED_INDEX_FIELD = "Interpolated Index";
  private static final String FIXED_RATE_FIELD = "Fixed Rate";
  private static final String PERIOD_TO_START_FIELD = "Period To Start";
  private static final String PERIOD_TO_END_FIELD = "Period To End";
  private static final String START_DATE_FIELD = "Start Date";
  private static final String END_DATE_FIELD = "End Date";
  private static final String DAY_COUNT_FIELD = "Day Count";

  //-------------------------------------------------------------------------
  /**
   * Parses a FRA from the CSV row.
   * 
   * @param row  the CSV row
   * @param info  the trade info
   * @param refData  the reference data
   * @return the loaded trades, all errors are captured in the result
   */
  static Trade parseFra(CsvRow row, TradeInfo info, ReferenceData refData) {
    BuySell buySell = row.findValue(BUY_SELL_FIELD).map(s -> BuySell.of(s)).orElse(BuySell.BUY);
    String notionalStr = row.getValue(NOTIONAL_FIELD);
    double notional = new BigDecimal(notionalStr).doubleValue();
    String fixedRateStr = row.getValue(FIXED_RATE_FIELD);
    double fixedRate = new BigDecimal(fixedRateStr).divide(BigDecimal.valueOf(100)).doubleValue();
    Optional<FraConvention> conventionOpt = row.findValue(CONVENTION_FIELD).map(s -> FraConvention.of(s));
    Optional<Period> periodToStartOpt = row.findValue(PERIOD_TO_START_FIELD).map(s -> Period.parse(s));
    Optional<Period> periodToEndOpt = row.findValue(PERIOD_TO_END_FIELD).map(s -> Period.parse(s));
    Optional<LocalDate> startDateOpt = row.findValue(START_DATE_FIELD).map(s -> TradeCsvLoader.parseDate(s));
    Optional<LocalDate> endDateOpt = row.findValue(END_DATE_FIELD).map(s -> TradeCsvLoader.parseDate(s));
    Optional<Currency> currencyOpt = row.findValue(CURRENCY_FIELD).map(s -> Currency.parse(s));
    Optional<IborIndex> indexOpt = row.findValue(INDEX_FIELD).map(s -> IborIndex.of(s));
    Optional<IborIndex> interpolatedOpt = row.findValue(INTERPOLATED_INDEX_FIELD).map(s -> IborIndex.of(s));
    Optional<DayCount> dayCountOpt = row.findValue(DAY_COUNT_FIELD).map(s -> DayCount.of(s));
    // not parsing businessDayAdjustment, paymentDate, fixingDateOffset, discounting

    // use convention if available
    if (conventionOpt.isPresent()) {
      if (currencyOpt.isPresent() || indexOpt.isPresent() || interpolatedOpt.isPresent() || dayCountOpt.isPresent()) {
        throw new IllegalArgumentException(
            "CSV file 'Fra' trade had invalid combination of fields. When '" + CONVENTION_FIELD +
                "' is present these fields must not be present: " +
                ImmutableList.of(CURRENCY_FIELD, INDEX_FIELD, INTERPOLATED_INDEX_FIELD, DAY_COUNT_FIELD));
      }
      FraConvention convention = conventionOpt.get();
      // explicit dates take precedence over relative ones
      if (startDateOpt.isPresent() && endDateOpt.isPresent()) {
        if (periodToStartOpt.isPresent() || periodToEndOpt.isPresent()) {
          throw new IllegalArgumentException(
              "CSV file 'Fra' trade had invalid combination of fields. When '" + CONVENTION_FIELD +
                  "' is present with '" + START_DATE_FIELD + "' and '" + END_DATE_FIELD +
                  "' then these fields must not be present: " +
                  ImmutableList.of(PERIOD_TO_START_FIELD, PERIOD_TO_END_FIELD));
        }
        LocalDate startDate = startDateOpt.get();
        LocalDate endDate = endDateOpt.get();
        // NOTE: payment date assumed to be the start date
        return convention.toTrade(info, startDate, endDate, startDate, buySell, notional, fixedRate);
      }
      // relative dates
      if (periodToStartOpt.isPresent() && info.getTradeDate().isPresent()) {
        if (startDateOpt.isPresent() || endDateOpt.isPresent()) {
          throw new IllegalArgumentException(
              "CSV file 'Fra' trade had invalid combination of fields. When '" + CONVENTION_FIELD +
                  "' is present with '" + PERIOD_TO_START_FIELD + "' and '" + TRADE_DATE_FIELD +
                  "' then these fields must not be present: " +
                  ImmutableList.of(START_DATE_FIELD, END_DATE_FIELD));
        }
        LocalDate tradeDate = info.getTradeDate().get();
        Period periodToStart = periodToStartOpt.get();
        Period periodToEnd = periodToEndOpt.orElse(periodToStart.plus(convention.getIndex().getTenor()));
        FraTrade trade = convention.createTrade(tradeDate, periodToStart, periodToEnd, buySell, notional, fixedRate, refData);
        return trade.toBuilder().info(info).build();
      }

    } else if (indexOpt.isPresent() && startDateOpt.isPresent() && endDateOpt.isPresent()) {
      IborIndex index = indexOpt.get();
      LocalDate startDate = startDateOpt.get();
      LocalDate endDate = endDateOpt.get();
      Fra.Builder builder = Fra.builder()
          .buySell(buySell)
          .notional(notional)
          .startDate(startDate)
          .endDate(endDate)
          .fixedRate(fixedRate)
          .index(index);
      currencyOpt.ifPresent(currency -> builder.currency(currency));
      interpolatedOpt.ifPresent(interpolated -> builder.indexInterpolated(interpolated));
      dayCountOpt.ifPresent(dayCount -> builder.dayCount(dayCount));
      return FraTrade.of(info, builder.build());
    }
    // no match
    throw new IllegalArgumentException(
        "CSV file 'Fra' trade had invalid combination of fields. These fields are mandatory:" +
            ImmutableList.of(BUY_SELL_FIELD, NOTIONAL_FIELD, FIXED_RATE_FIELD) +
            " and one of these combinations is mandatory: " +
            ImmutableList.of(CONVENTION_FIELD, TRADE_DATE_FIELD, PERIOD_TO_START_FIELD) +
            " or " +
            ImmutableList.of(CONVENTION_FIELD, START_DATE_FIELD, END_DATE_FIELD) +
            " or " +
            ImmutableList.of(INDEX_FIELD, START_DATE_FIELD, END_DATE_FIELD));
  }

  //-------------------------------------------------------------------------
  // Restricted constructor.
  private FraTradeCsvLoader() {
  }

}
