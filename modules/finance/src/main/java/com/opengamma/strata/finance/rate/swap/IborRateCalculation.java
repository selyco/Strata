/**
 * Copyright (C) 2014 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.finance.rate.swap;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.opengamma.strata.basics.value.ValueSchedule.ALWAYS_0;
import static com.opengamma.strata.basics.value.ValueSchedule.ALWAYS_1;
import static com.opengamma.strata.finance.rate.swap.IborRateAveragingMethod.UNWEIGHTED;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

import org.joda.beans.Bean;
import org.joda.beans.BeanDefinition;
import org.joda.beans.ImmutableBean;
import org.joda.beans.ImmutableDefaults;
import org.joda.beans.ImmutablePreBuild;
import org.joda.beans.JodaBeanUtils;
import org.joda.beans.MetaProperty;
import org.joda.beans.Property;
import org.joda.beans.PropertyDefinition;
import org.joda.beans.impl.direct.DirectFieldsBeanBuilder;
import org.joda.beans.impl.direct.DirectMetaBean;
import org.joda.beans.impl.direct.DirectMetaProperty;
import org.joda.beans.impl.direct.DirectMetaPropertyMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.opengamma.strata.basics.date.DayCount;
import com.opengamma.strata.basics.date.DaysAdjustment;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.basics.index.Index;
import com.opengamma.strata.basics.schedule.RollConvention;
import com.opengamma.strata.basics.schedule.Schedule;
import com.opengamma.strata.basics.schedule.SchedulePeriod;
import com.opengamma.strata.basics.value.ValueSchedule;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.finance.rate.FixedRateObservation;
import com.opengamma.strata.finance.rate.IborAveragedFixing;
import com.opengamma.strata.finance.rate.IborAveragedRateObservation;
import com.opengamma.strata.finance.rate.IborRateObservation;
import com.opengamma.strata.finance.rate.RateObservation;

/**
 * Defines the calculation of a floating rate swap leg based on an IBOR-like index.
 * <p>
 * This defines the data necessary to calculate the amount payable on the leg.
 * The amount is based on the observed value of an IBOR-like index such as 'GBP-LIBOR-3M' or 'EUR-EURIBOR-1M'.
 * <p>
 * The index is observed once for each <i>reset period</i> and referred to as a <i>fixing</i>.
 * The actual date of observation is the <i>fixing date</i>, which is relative to either
 * the start or end of the reset period.
 * <p>
 * The reset period is typically the same as the accrual period.
 * In this case, the rate for the accrual period is based directly on the fixing.
 * If the reset period is a subdivision of the accrual period then there are multiple fixings,
 * one for each reset period.
 * In that case, the rate for the accrual period is based on an average of the fixings.
 */
@BeanDefinition
public final class IborRateCalculation
    implements RateCalculation, ImmutableBean, Serializable {

  /**
   * The day count convention applicable.
   * <p>
   * This is used to convert dates to a numerical value.
   * <p>
   * When building, this will default to the day count of the index if not specified.
   */
  @PropertyDefinition(validate = "notNull")
  private final DayCount dayCount;
  /**
   * The IBOR-like index.
   * <p>
   * The rate to be paid is based on this index
   * It will be a well known market index such as 'GBP-LIBOR-3M'.
   */
  @PropertyDefinition(validate = "notNull")
  private final IborIndex index;
  /**
   * The reset schedule, used when averaging rates, optional.
   * <p>
   * Most swaps have a single fixing for each accrual period.
   * This property allows multiple fixings to be defined by dividing the accrual periods into reset periods.
   * <p>
   * If this property is not present, then the reset period is the same as the accrual period.
   * If this property is present, then the accrual period is divided as per the information
   * in the reset schedule, multiple fixing dates are calculated, and rate averaging performed.
   */
  @PropertyDefinition(get = "optional")
  private final ResetSchedule resetPeriods;
  /**
   * The base date that each fixing is made relative to, defaulted to 'PeriodStart'.
   * <p>
   * The fixing date is relative to either the start or end of each reset period.
   * <p>
   * Note that in most cases, the reset frequency matches the accrual frequency
   * and thus there is only one fixing for the accrual period.
   */
  @PropertyDefinition(validate = "notNull")
  private final FixingRelativeTo fixingRelativeTo;
  /**
   * The offset of the fixing date from each adjusted reset date.
   * <p>
   * The offset is applied to the base date specified by {@code fixingRelativeTo}.
   * The offset is typically a negative number of business days.
   * <p>
   * Note that in most cases, the reset frequency matches the accrual frequency
   * and thus there is only one fixing for the accrual period.
   * <p>
   * When building, this will default to the fixing offset of the index if not specified.
   */
  @PropertyDefinition(validate = "notNull")
  private final DaysAdjustment fixingOffset;
  /**
   * The negative rate method, defaulted to 'AllowNegative'.
   * <p>
   * This is used when the interest rate, observed or calculated, goes negative.
   * It does not apply if the rate is fixed, such as in a stub or using {@code firstRegularRate}.
   * <p>
   * Defined by the 2006 ISDA definitions article 6.4.
   */
  @PropertyDefinition(validate = "notNull")
  private final NegativeRateMethod negativeRateMethod;

  /**
   * The first rate of the first regular reset period, optional.
   * A 5% rate will be expressed as 0.05.
   * <p>
   * In certain circumstances two counterparties agree the rate of the first fixing when the contract starts.
   * The rate is applicable for the first reset period of the first <i>regular</i> accrual period.
   * It is used in place of an observed fixing.
   * Other calculation elements, such as gearing or spread, still apply.
   * After the first reset period, the rate is observed via the normal fixing process.
   * <p>
   * If the first floating rate applies to the initial stub rather than the regular accrual periods
   * it must be specified using {@code initialStub}.
   * <p>
   * If this property is not present, then the first rate is observed via the normal fixing process.
   */
  @PropertyDefinition(get = "optional")
  private final Double firstRegularRate;
  /**
   * The rate to be used in initial stub, optional.
   * <p>
   * The initial stub of a swap may have different rate rules to the regular accrual periods.
   * A fixed rate may be specified, a different floating rate or a linearly interpolated floating rate.
   * This may not be present if there is no initial stub, or if the index during the stub is the same
   * as the main floating rate index.
   * <p>
   * If this property is not present, then the main index applies during any initial stub.
   * If this property is present and there is no initial stub, it is ignored.
   */
  @PropertyDefinition(get = "optional")
  private final StubCalculation initialStub;
  /**
   * The rate to be used in final stub, optional.
   * <p>
   * The final stub of a swap may have different rate rules to the regular accrual periods.
   * A fixed rate may be specified, a different floating rate or a linearly interpolated floating rate.
   * This may not be present if there is no final stub, or if the index during the stub is the same
   * as the main floating rate index.
   * <p>
   * If this property is not present, then the main index applies during any final stub.
   * If this property is present and there is no final stub, it is ignored.
   */
  @PropertyDefinition(get = "optional")
  private final StubCalculation finalStub;
  /**
   * The gearing multiplier, optional.
   * <p>
   * This defines the gearing as an initial value and a list of adjustments.
   * The gearing is only permitted to change at accrual period boundaries.
   * <p>
   * When calculating the rate, the fixing rate is multiplied by the gearing.
   * A gearing of 1 has no effect.
   * If both gearing and spread exist, then the gearing is applied first.
   * <p>
   * If this property is not present, then no gearing applies.
   * <p>
   * Gearing is also known as <i>leverage</i>.
   */
  @PropertyDefinition(get = "optional")
  private final ValueSchedule gearing;
  /**
   * The spread rate, with a 5% rate expressed as 0.05, optional.
   * <p>
   * This defines the spread as an initial value and a list of adjustments.
   * The spread is only permitted to change at accrual period boundaries.
   * Spread is a per annum rate.
   * <p>
   * When calculating the rate, the spread is added to the fixing rate.
   * A spread of 0 has no effect.
   * If both gearing and spread exist, then the gearing is applied first.
   * <p>
   * If this property is not present, then no spread applies.
   * <p>
   * Defined by the 2006 ISDA definitions article 6.2e.
   */
  @PropertyDefinition(get = "optional")
  private final ValueSchedule spread;

  //-------------------------------------------------------------------------
  /**
   * Obtains a rate calculation for the specified index.
   * <p>
   * The calculation will use the day count and fixing offset of the index.
   * All optional fields will be set to their default values.
   * Thus, fixing will be in advance, with no spread, gearing or reset periods.
   * If this method provides insufficient control, use the {@linkplain #builder() builder}.
   * 
   * @param index  the index
   * @return the calculation
   */
  public static IborRateCalculation of(IborIndex index) {
    return IborRateCalculation.builder().index(index).build();
  }

  //-------------------------------------------------------------------------
  @ImmutableDefaults
  private static void applyDefaults(Builder builder) {
    builder.fixingRelativeTo(FixingRelativeTo.PERIOD_START);
    builder.negativeRateMethod(NegativeRateMethod.ALLOW_NEGATIVE);
  }

  @ImmutablePreBuild
  private static void preBuild(Builder builder) {
    if (builder.index != null) {
      if (builder.dayCount == null) {
        builder.dayCount = builder.index.getDayCount();
      }
      if (builder.fixingOffset == null) {
        builder.fixingOffset = builder.index.getFixingDateOffset();
      }
    }
  }

  //-------------------------------------------------------------------------
  @Override
  public SwapLegType getType() {
    return SwapLegType.IBOR;
  }

  @Override
  public void collectIndices(ImmutableSet.Builder<Index> builder) {
    builder.add(index);
    if (initialStub != null) {
      initialStub.getIndex().ifPresent(idx -> builder.add(idx));
      initialStub.getIndexInterpolated().ifPresent(idx -> builder.add(idx));
    }
    if (finalStub != null) {
      finalStub.getIndex().ifPresent(idx -> builder.add(idx));
      finalStub.getIndexInterpolated().ifPresent(idx -> builder.add(idx));
    }
  }

  @Override
  public ImmutableList<RateAccrualPeriod> expand(Schedule accrualSchedule, Schedule paymentSchedule) {
    ArgChecker.notNull(accrualSchedule, "accrualSchedule");
    ArgChecker.notNull(paymentSchedule, "paymentSchedule");
    // avoid null stub definitions if there are stubs
    Optional<SchedulePeriod> scheduleInitialStub = accrualSchedule.getInitialStub();
    Optional<SchedulePeriod> scheduleFinalStub = accrualSchedule.getFinalStub();
    if ((scheduleInitialStub.isPresent() && initialStub == null) ||
        (scheduleFinalStub.isPresent() && finalStub == null)) {
      return toBuilder()
          .initialStub(firstNonNull(initialStub, StubCalculation.NONE))
          .finalStub(firstNonNull(finalStub, StubCalculation.NONE))
          .build()
          .expand(accrualSchedule, paymentSchedule);
    }
    // resolve data by schedule
    List<Double> resolvedGearings = firstNonNull(gearing, ALWAYS_1).resolveValues(accrualSchedule.getPeriods());
    List<Double> resolvedSpreads = firstNonNull(spread, ALWAYS_0).resolveValues(accrualSchedule.getPeriods());
    // build accrual periods
    ImmutableList.Builder<RateAccrualPeriod> accrualPeriods = ImmutableList.builder();
    for (int i = 0; i < accrualSchedule.size(); i++) {
      SchedulePeriod period = accrualSchedule.getPeriod(i);
      accrualPeriods.add(RateAccrualPeriod.builder(period)
          .yearFraction(period.yearFraction(dayCount, accrualSchedule))
          .rateObservation(
              createRateObservation(
                  period, accrualSchedule.getRollConvention(), i, scheduleInitialStub, scheduleFinalStub))
          .negativeRateMethod(negativeRateMethod)
          .gearing(resolvedGearings.get(i))
          .spread(resolvedSpreads.get(i))
          .build());
    }
    return accrualPeriods.build();
  }

  // creates the rate observation
  private RateObservation createRateObservation(
      SchedulePeriod period,
      RollConvention rollConvention,
      int scheduleIndex,
      Optional<SchedulePeriod> scheduleInitialStub,
      Optional<SchedulePeriod> scheduleFinalStub) {

    LocalDate fixingDate = fixingOffset.adjust(fixingRelativeTo.selectBaseDate(period));
    // handle stubs
    if (scheduleInitialStub.isPresent() && scheduleInitialStub.get() == period) {
      return initialStub.createRateObservation(fixingDate, index);
    }
    if (scheduleFinalStub.isPresent() && scheduleFinalStub.get() == period) {
      return finalStub.createRateObservation(fixingDate, index);
    }
    // handle explicit reset periods, possible averaging
    if (resetPeriods != null) {
      return createRateObservationWithResetPeriods(
          period, rollConvention, isFirstRegularPeriod(scheduleIndex, scheduleInitialStub.isPresent()));
    }
    // handle possible fixed rate
    if (firstRegularRate != null && isFirstRegularPeriod(scheduleIndex, scheduleInitialStub.isPresent())) {
      return FixedRateObservation.of(firstRegularRate);
    }
    // simple Ibor
    return IborRateObservation.of((IborIndex) index, fixingDate);
  }

  // reset periods have been specified, which may or may not imply averaging
  private RateObservation createRateObservationWithResetPeriods(
      SchedulePeriod period,
      RollConvention rollConvention,
      boolean firstRegular) {

    Schedule resetSchedule = resetPeriods.createSchedule(period, rollConvention);
    List<IborAveragedFixing> fixings = new ArrayList<>();
    for (int i = 0; i < resetSchedule.size(); i++) {
      SchedulePeriod resetPeriod = resetSchedule.getPeriod(i);
      fixings.add(IborAveragedFixing.builder()
          .fixingDate(fixingOffset.adjust(fixingRelativeTo.selectBaseDate(resetPeriod)))
          .fixedRate(firstRegular && i == 0 ? firstRegularRate : null)
          .weight(resetPeriods.getAveragingMethod() == UNWEIGHTED ? 1 : resetPeriod.lengthInDays())
          .build());
    }
    return IborAveragedRateObservation.of(index, fixings);
  }

  // is the period the first regular period
  private boolean isFirstRegularPeriod(int scheduleIndex, boolean hasInitialStub) {
    if (hasInitialStub) {
      return scheduleIndex == 1;
    } else {
      return scheduleIndex == 0;
    }
  }

  //------------------------- AUTOGENERATED START -------------------------
  ///CLOVER:OFF
  /**
   * The meta-bean for {@code IborRateCalculation}.
   * @return the meta-bean, not null
   */
  public static IborRateCalculation.Meta meta() {
    return IborRateCalculation.Meta.INSTANCE;
  }

  static {
    JodaBeanUtils.registerMetaBean(IborRateCalculation.Meta.INSTANCE);
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  /**
   * Returns a builder used to create an instance of the bean.
   * @return the builder, not null
   */
  public static IborRateCalculation.Builder builder() {
    return new IborRateCalculation.Builder();
  }

  private IborRateCalculation(
      DayCount dayCount,
      IborIndex index,
      ResetSchedule resetPeriods,
      FixingRelativeTo fixingRelativeTo,
      DaysAdjustment fixingOffset,
      NegativeRateMethod negativeRateMethod,
      Double firstRegularRate,
      StubCalculation initialStub,
      StubCalculation finalStub,
      ValueSchedule gearing,
      ValueSchedule spread) {
    JodaBeanUtils.notNull(dayCount, "dayCount");
    JodaBeanUtils.notNull(index, "index");
    JodaBeanUtils.notNull(fixingRelativeTo, "fixingRelativeTo");
    JodaBeanUtils.notNull(fixingOffset, "fixingOffset");
    JodaBeanUtils.notNull(negativeRateMethod, "negativeRateMethod");
    this.dayCount = dayCount;
    this.index = index;
    this.resetPeriods = resetPeriods;
    this.fixingRelativeTo = fixingRelativeTo;
    this.fixingOffset = fixingOffset;
    this.negativeRateMethod = negativeRateMethod;
    this.firstRegularRate = firstRegularRate;
    this.initialStub = initialStub;
    this.finalStub = finalStub;
    this.gearing = gearing;
    this.spread = spread;
  }

  @Override
  public IborRateCalculation.Meta metaBean() {
    return IborRateCalculation.Meta.INSTANCE;
  }

  @Override
  public <R> Property<R> property(String propertyName) {
    return metaBean().<R>metaProperty(propertyName).createProperty(this);
  }

  @Override
  public Set<String> propertyNames() {
    return metaBean().metaPropertyMap().keySet();
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the day count convention applicable.
   * <p>
   * This is used to convert dates to a numerical value.
   * <p>
   * When building, this will default to the day count of the index if not specified.
   * @return the value of the property, not null
   */
  public DayCount getDayCount() {
    return dayCount;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the IBOR-like index.
   * <p>
   * The rate to be paid is based on this index
   * It will be a well known market index such as 'GBP-LIBOR-3M'.
   * @return the value of the property, not null
   */
  public IborIndex getIndex() {
    return index;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the reset schedule, used when averaging rates, optional.
   * <p>
   * Most swaps have a single fixing for each accrual period.
   * This property allows multiple fixings to be defined by dividing the accrual periods into reset periods.
   * <p>
   * If this property is not present, then the reset period is the same as the accrual period.
   * If this property is present, then the accrual period is divided as per the information
   * in the reset schedule, multiple fixing dates are calculated, and rate averaging performed.
   * @return the optional value of the property, not null
   */
  public Optional<ResetSchedule> getResetPeriods() {
    return Optional.ofNullable(resetPeriods);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the base date that each fixing is made relative to, defaulted to 'PeriodStart'.
   * <p>
   * The fixing date is relative to either the start or end of each reset period.
   * <p>
   * Note that in most cases, the reset frequency matches the accrual frequency
   * and thus there is only one fixing for the accrual period.
   * @return the value of the property, not null
   */
  public FixingRelativeTo getFixingRelativeTo() {
    return fixingRelativeTo;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the offset of the fixing date from each adjusted reset date.
   * <p>
   * The offset is applied to the base date specified by {@code fixingRelativeTo}.
   * The offset is typically a negative number of business days.
   * <p>
   * Note that in most cases, the reset frequency matches the accrual frequency
   * and thus there is only one fixing for the accrual period.
   * <p>
   * When building, this will default to the fixing offset of the index if not specified.
   * @return the value of the property, not null
   */
  public DaysAdjustment getFixingOffset() {
    return fixingOffset;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the negative rate method, defaulted to 'AllowNegative'.
   * <p>
   * This is used when the interest rate, observed or calculated, goes negative.
   * It does not apply if the rate is fixed, such as in a stub or using {@code firstRegularRate}.
   * <p>
   * Defined by the 2006 ISDA definitions article 6.4.
   * @return the value of the property, not null
   */
  public NegativeRateMethod getNegativeRateMethod() {
    return negativeRateMethod;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the first rate of the first regular reset period, optional.
   * A 5% rate will be expressed as 0.05.
   * <p>
   * In certain circumstances two counterparties agree the rate of the first fixing when the contract starts.
   * The rate is applicable for the first reset period of the first <i>regular</i> accrual period.
   * It is used in place of an observed fixing.
   * Other calculation elements, such as gearing or spread, still apply.
   * After the first reset period, the rate is observed via the normal fixing process.
   * <p>
   * If the first floating rate applies to the initial stub rather than the regular accrual periods
   * it must be specified using {@code initialStub}.
   * <p>
   * If this property is not present, then the first rate is observed via the normal fixing process.
   * @return the optional value of the property, not null
   */
  public OptionalDouble getFirstRegularRate() {
    return firstRegularRate != null ? OptionalDouble.of(firstRegularRate) : OptionalDouble.empty();
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the rate to be used in initial stub, optional.
   * <p>
   * The initial stub of a swap may have different rate rules to the regular accrual periods.
   * A fixed rate may be specified, a different floating rate or a linearly interpolated floating rate.
   * This may not be present if there is no initial stub, or if the index during the stub is the same
   * as the main floating rate index.
   * <p>
   * If this property is not present, then the main index applies during any initial stub.
   * If this property is present and there is no initial stub, it is ignored.
   * @return the optional value of the property, not null
   */
  public Optional<StubCalculation> getInitialStub() {
    return Optional.ofNullable(initialStub);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the rate to be used in final stub, optional.
   * <p>
   * The final stub of a swap may have different rate rules to the regular accrual periods.
   * A fixed rate may be specified, a different floating rate or a linearly interpolated floating rate.
   * This may not be present if there is no final stub, or if the index during the stub is the same
   * as the main floating rate index.
   * <p>
   * If this property is not present, then the main index applies during any final stub.
   * If this property is present and there is no final stub, it is ignored.
   * @return the optional value of the property, not null
   */
  public Optional<StubCalculation> getFinalStub() {
    return Optional.ofNullable(finalStub);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the gearing multiplier, optional.
   * <p>
   * This defines the gearing as an initial value and a list of adjustments.
   * The gearing is only permitted to change at accrual period boundaries.
   * <p>
   * When calculating the rate, the fixing rate is multiplied by the gearing.
   * A gearing of 1 has no effect.
   * If both gearing and spread exist, then the gearing is applied first.
   * <p>
   * If this property is not present, then no gearing applies.
   * <p>
   * Gearing is also known as <i>leverage</i>.
   * @return the optional value of the property, not null
   */
  public Optional<ValueSchedule> getGearing() {
    return Optional.ofNullable(gearing);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the spread rate, with a 5% rate expressed as 0.05, optional.
   * <p>
   * This defines the spread as an initial value and a list of adjustments.
   * The spread is only permitted to change at accrual period boundaries.
   * Spread is a per annum rate.
   * <p>
   * When calculating the rate, the spread is added to the fixing rate.
   * A spread of 0 has no effect.
   * If both gearing and spread exist, then the gearing is applied first.
   * <p>
   * If this property is not present, then no spread applies.
   * <p>
   * Defined by the 2006 ISDA definitions article 6.2e.
   * @return the optional value of the property, not null
   */
  public Optional<ValueSchedule> getSpread() {
    return Optional.ofNullable(spread);
  }

  //-----------------------------------------------------------------------
  /**
   * Returns a builder that allows this bean to be mutated.
   * @return the mutable builder, not null
   */
  public Builder toBuilder() {
    return new Builder(this);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj != null && obj.getClass() == this.getClass()) {
      IborRateCalculation other = (IborRateCalculation) obj;
      return JodaBeanUtils.equal(getDayCount(), other.getDayCount()) &&
          JodaBeanUtils.equal(getIndex(), other.getIndex()) &&
          JodaBeanUtils.equal(resetPeriods, other.resetPeriods) &&
          JodaBeanUtils.equal(getFixingRelativeTo(), other.getFixingRelativeTo()) &&
          JodaBeanUtils.equal(getFixingOffset(), other.getFixingOffset()) &&
          JodaBeanUtils.equal(getNegativeRateMethod(), other.getNegativeRateMethod()) &&
          JodaBeanUtils.equal(firstRegularRate, other.firstRegularRate) &&
          JodaBeanUtils.equal(initialStub, other.initialStub) &&
          JodaBeanUtils.equal(finalStub, other.finalStub) &&
          JodaBeanUtils.equal(gearing, other.gearing) &&
          JodaBeanUtils.equal(spread, other.spread);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash = hash * 31 + JodaBeanUtils.hashCode(getDayCount());
    hash = hash * 31 + JodaBeanUtils.hashCode(getIndex());
    hash = hash * 31 + JodaBeanUtils.hashCode(resetPeriods);
    hash = hash * 31 + JodaBeanUtils.hashCode(getFixingRelativeTo());
    hash = hash * 31 + JodaBeanUtils.hashCode(getFixingOffset());
    hash = hash * 31 + JodaBeanUtils.hashCode(getNegativeRateMethod());
    hash = hash * 31 + JodaBeanUtils.hashCode(firstRegularRate);
    hash = hash * 31 + JodaBeanUtils.hashCode(initialStub);
    hash = hash * 31 + JodaBeanUtils.hashCode(finalStub);
    hash = hash * 31 + JodaBeanUtils.hashCode(gearing);
    hash = hash * 31 + JodaBeanUtils.hashCode(spread);
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(384);
    buf.append("IborRateCalculation{");
    buf.append("dayCount").append('=').append(getDayCount()).append(',').append(' ');
    buf.append("index").append('=').append(getIndex()).append(',').append(' ');
    buf.append("resetPeriods").append('=').append(resetPeriods).append(',').append(' ');
    buf.append("fixingRelativeTo").append('=').append(getFixingRelativeTo()).append(',').append(' ');
    buf.append("fixingOffset").append('=').append(getFixingOffset()).append(',').append(' ');
    buf.append("negativeRateMethod").append('=').append(getNegativeRateMethod()).append(',').append(' ');
    buf.append("firstRegularRate").append('=').append(firstRegularRate).append(',').append(' ');
    buf.append("initialStub").append('=').append(initialStub).append(',').append(' ');
    buf.append("finalStub").append('=').append(finalStub).append(',').append(' ');
    buf.append("gearing").append('=').append(gearing).append(',').append(' ');
    buf.append("spread").append('=').append(JodaBeanUtils.toString(spread));
    buf.append('}');
    return buf.toString();
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code IborRateCalculation}.
   */
  public static final class Meta extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code dayCount} property.
     */
    private final MetaProperty<DayCount> dayCount = DirectMetaProperty.ofImmutable(
        this, "dayCount", IborRateCalculation.class, DayCount.class);
    /**
     * The meta-property for the {@code index} property.
     */
    private final MetaProperty<IborIndex> index = DirectMetaProperty.ofImmutable(
        this, "index", IborRateCalculation.class, IborIndex.class);
    /**
     * The meta-property for the {@code resetPeriods} property.
     */
    private final MetaProperty<ResetSchedule> resetPeriods = DirectMetaProperty.ofImmutable(
        this, "resetPeriods", IborRateCalculation.class, ResetSchedule.class);
    /**
     * The meta-property for the {@code fixingRelativeTo} property.
     */
    private final MetaProperty<FixingRelativeTo> fixingRelativeTo = DirectMetaProperty.ofImmutable(
        this, "fixingRelativeTo", IborRateCalculation.class, FixingRelativeTo.class);
    /**
     * The meta-property for the {@code fixingOffset} property.
     */
    private final MetaProperty<DaysAdjustment> fixingOffset = DirectMetaProperty.ofImmutable(
        this, "fixingOffset", IborRateCalculation.class, DaysAdjustment.class);
    /**
     * The meta-property for the {@code negativeRateMethod} property.
     */
    private final MetaProperty<NegativeRateMethod> negativeRateMethod = DirectMetaProperty.ofImmutable(
        this, "negativeRateMethod", IborRateCalculation.class, NegativeRateMethod.class);
    /**
     * The meta-property for the {@code firstRegularRate} property.
     */
    private final MetaProperty<Double> firstRegularRate = DirectMetaProperty.ofImmutable(
        this, "firstRegularRate", IborRateCalculation.class, Double.class);
    /**
     * The meta-property for the {@code initialStub} property.
     */
    private final MetaProperty<StubCalculation> initialStub = DirectMetaProperty.ofImmutable(
        this, "initialStub", IborRateCalculation.class, StubCalculation.class);
    /**
     * The meta-property for the {@code finalStub} property.
     */
    private final MetaProperty<StubCalculation> finalStub = DirectMetaProperty.ofImmutable(
        this, "finalStub", IborRateCalculation.class, StubCalculation.class);
    /**
     * The meta-property for the {@code gearing} property.
     */
    private final MetaProperty<ValueSchedule> gearing = DirectMetaProperty.ofImmutable(
        this, "gearing", IborRateCalculation.class, ValueSchedule.class);
    /**
     * The meta-property for the {@code spread} property.
     */
    private final MetaProperty<ValueSchedule> spread = DirectMetaProperty.ofImmutable(
        this, "spread", IborRateCalculation.class, ValueSchedule.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "dayCount",
        "index",
        "resetPeriods",
        "fixingRelativeTo",
        "fixingOffset",
        "negativeRateMethod",
        "firstRegularRate",
        "initialStub",
        "finalStub",
        "gearing",
        "spread");

    /**
     * Restricted constructor.
     */
    private Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case 1905311443:  // dayCount
          return dayCount;
        case 100346066:  // index
          return index;
        case -1272973693:  // resetPeriods
          return resetPeriods;
        case 232554996:  // fixingRelativeTo
          return fixingRelativeTo;
        case -317508960:  // fixingOffset
          return fixingOffset;
        case 1969081334:  // negativeRateMethod
          return negativeRateMethod;
        case 570227148:  // firstRegularRate
          return firstRegularRate;
        case 1233359378:  // initialStub
          return initialStub;
        case 355242820:  // finalStub
          return finalStub;
        case -91774989:  // gearing
          return gearing;
        case -895684237:  // spread
          return spread;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public IborRateCalculation.Builder builder() {
      return new IborRateCalculation.Builder();
    }

    @Override
    public Class<? extends IborRateCalculation> beanType() {
      return IborRateCalculation.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code dayCount} property.
     * @return the meta-property, not null
     */
    public MetaProperty<DayCount> dayCount() {
      return dayCount;
    }

    /**
     * The meta-property for the {@code index} property.
     * @return the meta-property, not null
     */
    public MetaProperty<IborIndex> index() {
      return index;
    }

    /**
     * The meta-property for the {@code resetPeriods} property.
     * @return the meta-property, not null
     */
    public MetaProperty<ResetSchedule> resetPeriods() {
      return resetPeriods;
    }

    /**
     * The meta-property for the {@code fixingRelativeTo} property.
     * @return the meta-property, not null
     */
    public MetaProperty<FixingRelativeTo> fixingRelativeTo() {
      return fixingRelativeTo;
    }

    /**
     * The meta-property for the {@code fixingOffset} property.
     * @return the meta-property, not null
     */
    public MetaProperty<DaysAdjustment> fixingOffset() {
      return fixingOffset;
    }

    /**
     * The meta-property for the {@code negativeRateMethod} property.
     * @return the meta-property, not null
     */
    public MetaProperty<NegativeRateMethod> negativeRateMethod() {
      return negativeRateMethod;
    }

    /**
     * The meta-property for the {@code firstRegularRate} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Double> firstRegularRate() {
      return firstRegularRate;
    }

    /**
     * The meta-property for the {@code initialStub} property.
     * @return the meta-property, not null
     */
    public MetaProperty<StubCalculation> initialStub() {
      return initialStub;
    }

    /**
     * The meta-property for the {@code finalStub} property.
     * @return the meta-property, not null
     */
    public MetaProperty<StubCalculation> finalStub() {
      return finalStub;
    }

    /**
     * The meta-property for the {@code gearing} property.
     * @return the meta-property, not null
     */
    public MetaProperty<ValueSchedule> gearing() {
      return gearing;
    }

    /**
     * The meta-property for the {@code spread} property.
     * @return the meta-property, not null
     */
    public MetaProperty<ValueSchedule> spread() {
      return spread;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case 1905311443:  // dayCount
          return ((IborRateCalculation) bean).getDayCount();
        case 100346066:  // index
          return ((IborRateCalculation) bean).getIndex();
        case -1272973693:  // resetPeriods
          return ((IborRateCalculation) bean).resetPeriods;
        case 232554996:  // fixingRelativeTo
          return ((IborRateCalculation) bean).getFixingRelativeTo();
        case -317508960:  // fixingOffset
          return ((IborRateCalculation) bean).getFixingOffset();
        case 1969081334:  // negativeRateMethod
          return ((IborRateCalculation) bean).getNegativeRateMethod();
        case 570227148:  // firstRegularRate
          return ((IborRateCalculation) bean).firstRegularRate;
        case 1233359378:  // initialStub
          return ((IborRateCalculation) bean).initialStub;
        case 355242820:  // finalStub
          return ((IborRateCalculation) bean).finalStub;
        case -91774989:  // gearing
          return ((IborRateCalculation) bean).gearing;
        case -895684237:  // spread
          return ((IborRateCalculation) bean).spread;
      }
      return super.propertyGet(bean, propertyName, quiet);
    }

    @Override
    protected void propertySet(Bean bean, String propertyName, Object newValue, boolean quiet) {
      metaProperty(propertyName);
      if (quiet) {
        return;
      }
      throw new UnsupportedOperationException("Property cannot be written: " + propertyName);
    }

  }

  //-----------------------------------------------------------------------
  /**
   * The bean-builder for {@code IborRateCalculation}.
   */
  public static final class Builder extends DirectFieldsBeanBuilder<IborRateCalculation> {

    private DayCount dayCount;
    private IborIndex index;
    private ResetSchedule resetPeriods;
    private FixingRelativeTo fixingRelativeTo;
    private DaysAdjustment fixingOffset;
    private NegativeRateMethod negativeRateMethod;
    private Double firstRegularRate;
    private StubCalculation initialStub;
    private StubCalculation finalStub;
    private ValueSchedule gearing;
    private ValueSchedule spread;

    /**
     * Restricted constructor.
     */
    private Builder() {
      applyDefaults(this);
    }

    /**
     * Restricted copy constructor.
     * @param beanToCopy  the bean to copy from, not null
     */
    private Builder(IborRateCalculation beanToCopy) {
      this.dayCount = beanToCopy.getDayCount();
      this.index = beanToCopy.getIndex();
      this.resetPeriods = beanToCopy.resetPeriods;
      this.fixingRelativeTo = beanToCopy.getFixingRelativeTo();
      this.fixingOffset = beanToCopy.getFixingOffset();
      this.negativeRateMethod = beanToCopy.getNegativeRateMethod();
      this.firstRegularRate = beanToCopy.firstRegularRate;
      this.initialStub = beanToCopy.initialStub;
      this.finalStub = beanToCopy.finalStub;
      this.gearing = beanToCopy.gearing;
      this.spread = beanToCopy.spread;
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case 1905311443:  // dayCount
          return dayCount;
        case 100346066:  // index
          return index;
        case -1272973693:  // resetPeriods
          return resetPeriods;
        case 232554996:  // fixingRelativeTo
          return fixingRelativeTo;
        case -317508960:  // fixingOffset
          return fixingOffset;
        case 1969081334:  // negativeRateMethod
          return negativeRateMethod;
        case 570227148:  // firstRegularRate
          return firstRegularRate;
        case 1233359378:  // initialStub
          return initialStub;
        case 355242820:  // finalStub
          return finalStub;
        case -91774989:  // gearing
          return gearing;
        case -895684237:  // spread
          return spread;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
    }

    @Override
    public Builder set(String propertyName, Object newValue) {
      switch (propertyName.hashCode()) {
        case 1905311443:  // dayCount
          this.dayCount = (DayCount) newValue;
          break;
        case 100346066:  // index
          this.index = (IborIndex) newValue;
          break;
        case -1272973693:  // resetPeriods
          this.resetPeriods = (ResetSchedule) newValue;
          break;
        case 232554996:  // fixingRelativeTo
          this.fixingRelativeTo = (FixingRelativeTo) newValue;
          break;
        case -317508960:  // fixingOffset
          this.fixingOffset = (DaysAdjustment) newValue;
          break;
        case 1969081334:  // negativeRateMethod
          this.negativeRateMethod = (NegativeRateMethod) newValue;
          break;
        case 570227148:  // firstRegularRate
          this.firstRegularRate = (Double) newValue;
          break;
        case 1233359378:  // initialStub
          this.initialStub = (StubCalculation) newValue;
          break;
        case 355242820:  // finalStub
          this.finalStub = (StubCalculation) newValue;
          break;
        case -91774989:  // gearing
          this.gearing = (ValueSchedule) newValue;
          break;
        case -895684237:  // spread
          this.spread = (ValueSchedule) newValue;
          break;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
      return this;
    }

    @Override
    public Builder set(MetaProperty<?> property, Object value) {
      super.set(property, value);
      return this;
    }

    @Override
    public Builder setString(String propertyName, String value) {
      setString(meta().metaProperty(propertyName), value);
      return this;
    }

    @Override
    public Builder setString(MetaProperty<?> property, String value) {
      super.setString(property, value);
      return this;
    }

    @Override
    public Builder setAll(Map<String, ? extends Object> propertyValueMap) {
      super.setAll(propertyValueMap);
      return this;
    }

    @Override
    public IborRateCalculation build() {
      preBuild(this);
      return new IborRateCalculation(
          dayCount,
          index,
          resetPeriods,
          fixingRelativeTo,
          fixingOffset,
          negativeRateMethod,
          firstRegularRate,
          initialStub,
          finalStub,
          gearing,
          spread);
    }

    //-----------------------------------------------------------------------
    /**
     * Sets the {@code dayCount} property in the builder.
     * @param dayCount  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder dayCount(DayCount dayCount) {
      JodaBeanUtils.notNull(dayCount, "dayCount");
      this.dayCount = dayCount;
      return this;
    }

    /**
     * Sets the {@code index} property in the builder.
     * @param index  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder index(IborIndex index) {
      JodaBeanUtils.notNull(index, "index");
      this.index = index;
      return this;
    }

    /**
     * Sets the {@code resetPeriods} property in the builder.
     * @param resetPeriods  the new value
     * @return this, for chaining, not null
     */
    public Builder resetPeriods(ResetSchedule resetPeriods) {
      this.resetPeriods = resetPeriods;
      return this;
    }

    /**
     * Sets the {@code fixingRelativeTo} property in the builder.
     * @param fixingRelativeTo  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder fixingRelativeTo(FixingRelativeTo fixingRelativeTo) {
      JodaBeanUtils.notNull(fixingRelativeTo, "fixingRelativeTo");
      this.fixingRelativeTo = fixingRelativeTo;
      return this;
    }

    /**
     * Sets the {@code fixingOffset} property in the builder.
     * @param fixingOffset  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder fixingOffset(DaysAdjustment fixingOffset) {
      JodaBeanUtils.notNull(fixingOffset, "fixingOffset");
      this.fixingOffset = fixingOffset;
      return this;
    }

    /**
     * Sets the {@code negativeRateMethod} property in the builder.
     * @param negativeRateMethod  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder negativeRateMethod(NegativeRateMethod negativeRateMethod) {
      JodaBeanUtils.notNull(negativeRateMethod, "negativeRateMethod");
      this.negativeRateMethod = negativeRateMethod;
      return this;
    }

    /**
     * Sets the {@code firstRegularRate} property in the builder.
     * @param firstRegularRate  the new value
     * @return this, for chaining, not null
     */
    public Builder firstRegularRate(Double firstRegularRate) {
      this.firstRegularRate = firstRegularRate;
      return this;
    }

    /**
     * Sets the {@code initialStub} property in the builder.
     * @param initialStub  the new value
     * @return this, for chaining, not null
     */
    public Builder initialStub(StubCalculation initialStub) {
      this.initialStub = initialStub;
      return this;
    }

    /**
     * Sets the {@code finalStub} property in the builder.
     * @param finalStub  the new value
     * @return this, for chaining, not null
     */
    public Builder finalStub(StubCalculation finalStub) {
      this.finalStub = finalStub;
      return this;
    }

    /**
     * Sets the {@code gearing} property in the builder.
     * @param gearing  the new value
     * @return this, for chaining, not null
     */
    public Builder gearing(ValueSchedule gearing) {
      this.gearing = gearing;
      return this;
    }

    /**
     * Sets the {@code spread} property in the builder.
     * @param spread  the new value
     * @return this, for chaining, not null
     */
    public Builder spread(ValueSchedule spread) {
      this.spread = spread;
      return this;
    }

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(384);
      buf.append("IborRateCalculation.Builder{");
      buf.append("dayCount").append('=').append(JodaBeanUtils.toString(dayCount)).append(',').append(' ');
      buf.append("index").append('=').append(JodaBeanUtils.toString(index)).append(',').append(' ');
      buf.append("resetPeriods").append('=').append(JodaBeanUtils.toString(resetPeriods)).append(',').append(' ');
      buf.append("fixingRelativeTo").append('=').append(JodaBeanUtils.toString(fixingRelativeTo)).append(',').append(' ');
      buf.append("fixingOffset").append('=').append(JodaBeanUtils.toString(fixingOffset)).append(',').append(' ');
      buf.append("negativeRateMethod").append('=').append(JodaBeanUtils.toString(negativeRateMethod)).append(',').append(' ');
      buf.append("firstRegularRate").append('=').append(JodaBeanUtils.toString(firstRegularRate)).append(',').append(' ');
      buf.append("initialStub").append('=').append(JodaBeanUtils.toString(initialStub)).append(',').append(' ');
      buf.append("finalStub").append('=').append(JodaBeanUtils.toString(finalStub)).append(',').append(' ');
      buf.append("gearing").append('=').append(JodaBeanUtils.toString(gearing)).append(',').append(' ');
      buf.append("spread").append('=').append(JodaBeanUtils.toString(spread));
      buf.append('}');
      return buf.toString();
    }

  }

  ///CLOVER:ON
  //-------------------------- AUTOGENERATED END --------------------------
}