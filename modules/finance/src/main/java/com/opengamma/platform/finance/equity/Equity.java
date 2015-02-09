/**
 * Copyright (C) 2014 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.platform.finance.equity;

import java.io.Serializable;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.joda.beans.Bean;
import org.joda.beans.BeanDefinition;
import org.joda.beans.DerivedProperty;
import org.joda.beans.ImmutableBean;
import org.joda.beans.JodaBeanUtils;
import org.joda.beans.MetaProperty;
import org.joda.beans.Property;
import org.joda.beans.PropertyDefinition;
import org.joda.beans.impl.direct.DirectFieldsBeanBuilder;
import org.joda.beans.impl.direct.DirectMetaBean;
import org.joda.beans.impl.direct.DirectMetaProperty;
import org.joda.beans.impl.direct.DirectMetaPropertyMap;

import com.google.common.collect.ImmutableMap;
import com.opengamma.basics.currency.Currency;
import com.opengamma.collect.id.StandardId;
import com.opengamma.collect.id.StandardIdentifiable;
import com.opengamma.platform.finance.Security;
import com.opengamma.platform.finance.SecurityType;

/**
 * An equity share of a company.
 * <p>
 * This represents the concept of a single equity share of a company.
 * For example, a single share of IBM.
 */
@BeanDefinition
public final class Equity
    implements Security, StandardIdentifiable, ImmutableBean, Serializable {

  /**
   * The security type constant for this class.
   */
  public static final SecurityType TYPE = SecurityType.of("Equity");

  /**
   * The primary standard identifier for the security.
   * <p>
   * The standard identifier is used to identify the security.
   * It will typically be an identifier in an external data system.
   */
  @PropertyDefinition(validate = "notNull", overrideGet = true)
  private final StandardId standardId;
  /**
   * The extensible set of attributes.
   * <p>
   * Most data is available as bean properties.
   * Attributes are used to tag the object with additional information.
   */
  @PropertyDefinition(validate = "notNull", overrideGet = true)
  private final ImmutableMap<String, String> attributes;

  /**
   * The company name.
   */
  @PropertyDefinition(validate = "notNull")
  private final String companyName;
  /**
   * The currency that the equity is quoted in.
   */
  @PropertyDefinition(validate = "notNull")
  private final Currency currency;

  //-------------------------------------------------------------------------
  /**
   * Gets the security type.
   * 
   * @return {@link #TYPE}
   */
  @Override
  @DerivedProperty
  public SecurityType getSecurityType() {
    return TYPE;
  }

  //------------------------- AUTOGENERATED START -------------------------
  ///CLOVER:OFF
  /**
   * The meta-bean for {@code Equity}.
   * @return the meta-bean, not null
   */
  public static Equity.Meta meta() {
    return Equity.Meta.INSTANCE;
  }

  static {
    JodaBeanUtils.registerMetaBean(Equity.Meta.INSTANCE);
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  /**
   * Returns a builder used to create an instance of the bean.
   * @return the builder, not null
   */
  public static Equity.Builder builder() {
    return new Equity.Builder();
  }

  private Equity(
      StandardId standardId,
      Map<String, String> attributes,
      String companyName,
      Currency currency) {
    JodaBeanUtils.notNull(standardId, "standardId");
    JodaBeanUtils.notNull(attributes, "attributes");
    JodaBeanUtils.notNull(companyName, "companyName");
    JodaBeanUtils.notNull(currency, "currency");
    this.standardId = standardId;
    this.attributes = ImmutableMap.copyOf(attributes);
    this.companyName = companyName;
    this.currency = currency;
  }

  @Override
  public Equity.Meta metaBean() {
    return Equity.Meta.INSTANCE;
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
   * Gets the primary standard identifier for the security.
   * <p>
   * The standard identifier is used to identify the security.
   * It will typically be an identifier in an external data system.
   * @return the value of the property, not null
   */
  @Override
  public StandardId getStandardId() {
    return standardId;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the extensible set of attributes.
   * <p>
   * Most data is available as bean properties.
   * Attributes are used to tag the object with additional information.
   * @return the value of the property, not null
   */
  @Override
  public ImmutableMap<String, String> getAttributes() {
    return attributes;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the company name.
   * @return the value of the property, not null
   */
  public String getCompanyName() {
    return companyName;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the currency that the equity is quoted in.
   * @return the value of the property, not null
   */
  public Currency getCurrency() {
    return currency;
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
      Equity other = (Equity) obj;
      return JodaBeanUtils.equal(getStandardId(), other.getStandardId()) &&
          JodaBeanUtils.equal(getAttributes(), other.getAttributes()) &&
          JodaBeanUtils.equal(getCompanyName(), other.getCompanyName()) &&
          JodaBeanUtils.equal(getCurrency(), other.getCurrency());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash = hash * 31 + JodaBeanUtils.hashCode(getStandardId());
    hash = hash * 31 + JodaBeanUtils.hashCode(getAttributes());
    hash = hash * 31 + JodaBeanUtils.hashCode(getCompanyName());
    hash = hash * 31 + JodaBeanUtils.hashCode(getCurrency());
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(192);
    buf.append("Equity{");
    buf.append("standardId").append('=').append(getStandardId()).append(',').append(' ');
    buf.append("attributes").append('=').append(getAttributes()).append(',').append(' ');
    buf.append("companyName").append('=').append(getCompanyName()).append(',').append(' ');
    buf.append("currency").append('=').append(getCurrency()).append(',').append(' ');
    buf.append("securityType").append('=').append(JodaBeanUtils.toString(getSecurityType()));
    buf.append('}');
    return buf.toString();
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code Equity}.
   */
  public static final class Meta extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code standardId} property.
     */
    private final MetaProperty<StandardId> standardId = DirectMetaProperty.ofImmutable(
        this, "standardId", Equity.class, StandardId.class);
    /**
     * The meta-property for the {@code attributes} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<ImmutableMap<String, String>> attributes = DirectMetaProperty.ofImmutable(
        this, "attributes", Equity.class, (Class) ImmutableMap.class);
    /**
     * The meta-property for the {@code companyName} property.
     */
    private final MetaProperty<String> companyName = DirectMetaProperty.ofImmutable(
        this, "companyName", Equity.class, String.class);
    /**
     * The meta-property for the {@code currency} property.
     */
    private final MetaProperty<Currency> currency = DirectMetaProperty.ofImmutable(
        this, "currency", Equity.class, Currency.class);
    /**
     * The meta-property for the {@code securityType} property.
     */
    private final MetaProperty<SecurityType> securityType = DirectMetaProperty.ofDerived(
        this, "securityType", Equity.class, SecurityType.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "standardId",
        "attributes",
        "companyName",
        "currency",
        "securityType");

    /**
     * Restricted constructor.
     */
    private Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case -1284477768:  // standardId
          return standardId;
        case 405645655:  // attributes
          return attributes;
        case -508582744:  // companyName
          return companyName;
        case 575402001:  // currency
          return currency;
        case 808245914:  // securityType
          return securityType;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public Equity.Builder builder() {
      return new Equity.Builder();
    }

    @Override
    public Class<? extends Equity> beanType() {
      return Equity.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code standardId} property.
     * @return the meta-property, not null
     */
    public MetaProperty<StandardId> standardId() {
      return standardId;
    }

    /**
     * The meta-property for the {@code attributes} property.
     * @return the meta-property, not null
     */
    public MetaProperty<ImmutableMap<String, String>> attributes() {
      return attributes;
    }

    /**
     * The meta-property for the {@code companyName} property.
     * @return the meta-property, not null
     */
    public MetaProperty<String> companyName() {
      return companyName;
    }

    /**
     * The meta-property for the {@code currency} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Currency> currency() {
      return currency;
    }

    /**
     * The meta-property for the {@code securityType} property.
     * @return the meta-property, not null
     */
    public MetaProperty<SecurityType> securityType() {
      return securityType;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case -1284477768:  // standardId
          return ((Equity) bean).getStandardId();
        case 405645655:  // attributes
          return ((Equity) bean).getAttributes();
        case -508582744:  // companyName
          return ((Equity) bean).getCompanyName();
        case 575402001:  // currency
          return ((Equity) bean).getCurrency();
        case 808245914:  // securityType
          return ((Equity) bean).getSecurityType();
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
   * The bean-builder for {@code Equity}.
   */
  public static final class Builder extends DirectFieldsBeanBuilder<Equity> {

    private StandardId standardId;
    private Map<String, String> attributes = ImmutableMap.of();
    private String companyName;
    private Currency currency;

    /**
     * Restricted constructor.
     */
    private Builder() {
    }

    /**
     * Restricted copy constructor.
     * @param beanToCopy  the bean to copy from, not null
     */
    private Builder(Equity beanToCopy) {
      this.standardId = beanToCopy.getStandardId();
      this.attributes = beanToCopy.getAttributes();
      this.companyName = beanToCopy.getCompanyName();
      this.currency = beanToCopy.getCurrency();
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case -1284477768:  // standardId
          return standardId;
        case 405645655:  // attributes
          return attributes;
        case -508582744:  // companyName
          return companyName;
        case 575402001:  // currency
          return currency;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Builder set(String propertyName, Object newValue) {
      switch (propertyName.hashCode()) {
        case -1284477768:  // standardId
          this.standardId = (StandardId) newValue;
          break;
        case 405645655:  // attributes
          this.attributes = (Map<String, String>) newValue;
          break;
        case -508582744:  // companyName
          this.companyName = (String) newValue;
          break;
        case 575402001:  // currency
          this.currency = (Currency) newValue;
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
    public Equity build() {
      return new Equity(
          standardId,
          attributes,
          companyName,
          currency);
    }

    //-----------------------------------------------------------------------
    /**
     * Sets the {@code standardId} property in the builder.
     * @param standardId  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder standardId(StandardId standardId) {
      JodaBeanUtils.notNull(standardId, "standardId");
      this.standardId = standardId;
      return this;
    }

    /**
     * Sets the {@code attributes} property in the builder.
     * @param attributes  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder attributes(Map<String, String> attributes) {
      JodaBeanUtils.notNull(attributes, "attributes");
      this.attributes = attributes;
      return this;
    }

    /**
     * Sets the {@code companyName} property in the builder.
     * @param companyName  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder companyName(String companyName) {
      JodaBeanUtils.notNull(companyName, "companyName");
      this.companyName = companyName;
      return this;
    }

    /**
     * Sets the {@code currency} property in the builder.
     * @param currency  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder currency(Currency currency) {
      JodaBeanUtils.notNull(currency, "currency");
      this.currency = currency;
      return this;
    }

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(160);
      buf.append("Equity.Builder{");
      buf.append("standardId").append('=').append(JodaBeanUtils.toString(standardId)).append(',').append(' ');
      buf.append("attributes").append('=').append(JodaBeanUtils.toString(attributes)).append(',').append(' ');
      buf.append("companyName").append('=').append(JodaBeanUtils.toString(companyName)).append(',').append(' ');
      buf.append("currency").append('=').append(JodaBeanUtils.toString(currency));
      buf.append('}');
      return buf.toString();
    }

  }

  ///CLOVER:ON
  //-------------------------- AUTOGENERATED END --------------------------
}