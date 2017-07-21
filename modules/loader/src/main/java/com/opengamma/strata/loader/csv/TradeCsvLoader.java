/*
 * Copyright (C) 2017 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.loader.csv;

import static java.util.stream.Collectors.toList;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import com.google.common.collect.ImmutableList;
import com.google.common.io.CharSource;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.StandardId;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.Guavate;
import com.opengamma.strata.collect.io.CsvIterator;
import com.opengamma.strata.collect.io.CsvRow;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.collect.io.UnicodeBom;
import com.opengamma.strata.collect.result.FailureItem;
import com.opengamma.strata.collect.result.FailureReason;
import com.opengamma.strata.collect.result.ValueWithFailures;
import com.opengamma.strata.product.Trade;
import com.opengamma.strata.product.TradeInfo;
import com.opengamma.strata.product.TradeInfoBuilder;

/**
 * Loads trades from CSV files.
 * <p>
 * The trades are expected to be in a CSV format known to Strata.
 * The parser is flexible, understanding a number of different ways to define each trade.
 * <p>
 * The following headers are understood and may occur in any order:<br />
 * <ul>
 * <li>The 'Type' column is required, and must be the instrument type,
 *   such as 'Fra' or 'Swap'
 * <li>The 'Trade Id Scheme' column is optional, and is the name of the scheme that the trade
 *   identifier is unique within, such as 'OG-Trade'
 * <li>The 'Trade Id' column is optional, and is the identifier of the trade,
 *   such as 'FRA12345'
 * <li>The 'Trade Date' column is optional, and is the date that the trade occurred,
 *   such as '2017-08-01'
 * <li>The 'Trade Time' column is optional, and is the time of day that the trade occurred,
 *   such as '11:30'
 * <li>The 'Trade Zone' column is optional, and is the time-zone that the trade occurred,
 *   such as 'Europe/London'
 * </ul>
 * <p>
 * CSV files sometimes contain a Unicode Byte Order Mark.
 * Callers are responsible for handling this, such as by using {@link UnicodeBom}.
 */
public final class TradeCsvLoader {

  private static final DateTimeFormatter DMY_SLASH = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH);

  // shared CSV headers
  static final String TRADE_DATE_FIELD = "Trade Date";
  static final String CONVENTION_FIELD = "Convention";
  static final String BUY_SELL_FIELD = "Buy Sell";
  static final String CURRENCY_FIELD = "Currency";
  static final String NOTIONAL_FIELD = "Notional";
  static final String INDEX_FIELD = "Index";
  static final String INTERPOLATED_INDEX_FIELD = "Interpolated Index";
  static final String FIXED_RATE_FIELD = "Fixed Rate";
  static final String PERIOD_TO_START_FIELD = "Period To Start";
  static final String TENOR_FIELD = "Tenor";
  static final String START_DATE_FIELD = "Start Date";
  static final String END_DATE_FIELD = "End Date";
  static final String BDC_FIELD = "Date Convention";
  static final String BDC_CAL_FIELD = "Date Calendar";
  static final String DAY_COUNT_FIELD = "Day Count";

  // CSV column headers
  private static final String TYPE_FIELD = "Type";
  private static final String ID_SCHEME_FIELD = "Id Scheme";
  private static final String ID_FIELD = "Id";
  private static final String TRADE_TIME_FIELD = "Trade Time";
  private static final String TRADE_ZONE_FIELD = "Trade Zone";

  /**
   * The reference data.
   */
  private final ReferenceData refData;

  //-------------------------------------------------------------------------
  /**
   * Obtains an instance that uses the standard set of reference data.
   * 
   * @return the loader
   */
  public static TradeCsvLoader of() {
    return new TradeCsvLoader(ReferenceData.standard());
  }

  /**
   * Obtains an instance that uses the standard set of reference data.
   * 
   * @param refData  the reference data
   * @return the loader
   */
  public static TradeCsvLoader of(ReferenceData refData) {
    return new TradeCsvLoader(refData);
  }

  //-------------------------------------------------------------------------
  /**
   * Restricted constructor.
   * 
   * @param refData  the reference data
   */
  private TradeCsvLoader(ReferenceData refData) {
    this.refData = ArgChecker.notNull(refData, "refData");
  }

  //-------------------------------------------------------------------------
  /**
   * Loads one or more CSV format trade files.
   * 
   * @param resources  the CSV resources
   * @return the loaded trades, trade-level errors are captured in the result
   */
  public ValueWithFailures<List<Trade>> load(ResourceLocator... resources) {
    return load(Arrays.asList(resources));
  }

  /**
   * Loads one or more CSV format trade files.
   * 
   * @param resources  the CSV resources
   * @return the loaded trades, all errors are captured in the result
   */
  public ValueWithFailures<List<Trade>> load(Collection<ResourceLocator> resources) {
    Collection<CharSource> charSources = resources.stream().map(r -> r.getCharSource()).collect(toList());
    return parse(charSources);
  }

  //-------------------------------------------------------------------------
  /**
   * Parses one or more CSV format trade files.
   * 
   * @param charSources  the CSV character sources
   * @return the loaded trades, all errors are captured in the result
   */
  public ValueWithFailures<List<Trade>> parse(Collection<CharSource> charSources) {
    return parse(charSources, tradeType -> true);
  }

  /**
   * Parses one or more CSV format trade files.
   * <p>
   * A predicate is specified that can be used to filter the trades based on the trade type.
   * 
   * @param charSources  the CSV character sources
   * @param tradeTypePredicate  the predicate used to select the trade type, returns true to retain, false to drop
   * @return the loaded trades, all errors are captured in the result
   */
  public ValueWithFailures<List<Trade>> parse(Collection<CharSource> charSources, Predicate<String> tradeTypePredicate) {
    try {
      ValueWithFailures<List<Trade>> result = ValueWithFailures.of(ImmutableList.of());
      for (CharSource charSource : charSources) {
        ValueWithFailures<List<Trade>> singleResult = parseFile(charSource, tradeTypePredicate);
        result = result.combinedWith(singleResult, Guavate::concatToList);
      }
      return result;

    } catch (RuntimeException ex) {
      return ValueWithFailures.of(ImmutableList.of(), FailureItem.of(FailureReason.ERROR, ex));
    }
  }

  // loads a single CSV file, filtering by trade type
  private ValueWithFailures<List<Trade>> parseFile(CharSource charSource, Predicate<String> tradeTypePredicate) {
    try (CsvIterator csv = CsvIterator.of(charSource, true)) {
      if (!csv.headers().contains(TYPE_FIELD)) {
        return ValueWithFailures.of(ImmutableList.of(),
            FailureItem.of(FailureReason.PARSING, "CSV file does not contain 'Type' header: {}", charSource));
      }
      return parseFile(csv);

    } catch (RuntimeException ex) {
      return ValueWithFailures.of(ImmutableList.of(),
          FailureItem.of(FailureReason.PARSING, ex, "CSV file could not be parsed: {}", charSource));
    }
  }

  // loads a single CSV file
  private ValueWithFailures<List<Trade>> parseFile(CsvIterator csv) {
    List<Trade> trades = new ArrayList<>();
    List<FailureItem> failures = new ArrayList<>();
    int line = 2;
    for (CsvRow row : (Iterable<CsvRow>) () -> csv) {
      try {
        String type = row.getField(TYPE_FIELD).toUpperCase(Locale.ENGLISH);
        TradeInfo info = parseTradeInfo(row);
        switch (type.toUpperCase(Locale.ENGLISH)) {
          case "FRA":
            trades.add(FraTradeCsvLoader.parse(row, info, refData));
            break;
          case "SWAP":
            trades.add(SwapTradeCsvLoader.parse(row, info, refData));
            break;
          case "TERMDEPOSIT":
            trades.add(TermDepositTradeCsvLoader.parse(row, info, refData));
            break;
          default:
            failures.add(FailureItem.of(FailureReason.PARSING, "CSV file trade type '{}' is not known at line {}", type, line));
            break;
        }
        line++;
      } catch (RuntimeException ex) {
        failures.add(FailureItem.of(FailureReason.PARSING, ex, "CSV file trade could not be parsed at line {}", line));
      }
    }
    return ValueWithFailures.of(trades, failures);
  }

  // parse the trade info
  private TradeInfo parseTradeInfo(CsvRow row) {
    TradeInfoBuilder infoBuilder = TradeInfo.builder();
    String scheme = row.findField(ID_SCHEME_FIELD).orElse("OG-Trade");
    row.findValue(ID_FIELD).ifPresent(id -> infoBuilder.id(StandardId.of(scheme, id)));
    row.findValue(TRADE_DATE_FIELD).ifPresent(dateStr -> infoBuilder.tradeDate(parseDate(dateStr)));
    row.findValue(TRADE_TIME_FIELD).ifPresent(timeStr -> infoBuilder.tradeTime(LocalTime.parse(timeStr)));
    row.findValue(TRADE_ZONE_FIELD).ifPresent(zoneStr -> infoBuilder.zone(ZoneId.of(zoneStr)));
    return infoBuilder.build();
  }

  // parses a date
  static LocalDate parseDate(String dateStr) {
    try {
      if (dateStr.contains("/")) {
        return LocalDate.parse(dateStr, DMY_SLASH);
      } else {
        return LocalDate.parse(dateStr);
      }
    } catch (DateTimeParseException ex) {
      throw new DateTimeException(
          "Unable to parse date, must be formatted as yyyy-MM-dd or dd/MM/yyyy, but was '" + dateStr + "'", ex);
    }
  }

}
