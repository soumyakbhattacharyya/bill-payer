package com.serviceco.coex.payment.support;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.String.valueOf;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQuery;
import java.time.temporal.WeekFields;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.google.common.base.Preconditions;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.serviceco.coex.exception.CoexRuntimeException;
import com.serviceco.coex.model.DateDimension;
import com.serviceco.coex.model.QDateDimension;
import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.model.constant.PeriodType;
import com.serviceco.coex.model.dto.Period;

import lombok.Getter;
import lombok.Setter;

@Component
public class DateTimeSupport {

  @Getter
  @Setter
  public static final class PeriodCategory {

    public static PeriodCategory factory(Integer month, Integer year) {

      final PeriodCategory category = new PeriodCategory();
      category.setYear(year);
      category.setMonth(month);
      return category;
    }

    private boolean current;

    private boolean arrear;

    private Integer year;

    private Integer month;

    private Integer quarter;

    private Integer week;

    private Integer day;

  }

  public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {

    final Set<Object> seen = ConcurrentHashMap.newKeySet();
    return t -> seen.add(keyExtractor.apply(t));
  }

  private final static String DEFAULT_ZONE_ID = ZoneId.systemDefault().getId();

  @PersistenceContext
  EntityManager em;

  public DateTimeSupport() {

    super();
  }

  private Clock buildClock(String dd, String mm, String yyyy, String zomeId) {

    final DateTimeFormatter FMT = new DateTimeFormatterBuilder().appendPattern("dd-mm-yyyy").parseDefaulting(ChronoField.NANO_OF_DAY, 0).toFormatter().withZone(ZoneId.of(zomeId));
    final Instant fixedInstant = FMT.parse(dd + "-" + mm + "-" + yyyy, Instant::from);
    return Clock.fixed(fixedInstant, ZoneId.of(zomeId));
  }

  private static Clock buildClock(ZoneId zoneId) {

    return Clock.system(zoneId);
  }

  public DateDimension correspondingDateDimension(final LocalDateTime now) {

    final int thisDay = now.getDayOfMonth();
    final int thisMonth = now.getMonthValue();
    final int thisYear = now.getYear();

    final QDateDimension dateDimension = QDateDimension.dateDimension;

    //@formatter:off
    return getQueryFactory().select(dateDimension)
                            .from(dateDimension)
                            .where(dateDimension.dayOfMonth.eq(thisDay)
                             .and(dateDimension.month.eq(thisMonth)
                             .and(dateDimension.isoYear.eq(thisYear))))
                            .fetchOne();
    //@formatter:on
  }

  public DateDimension correspondingDateDimension(final LocalDate now) {
    if (now == null) {
      return null;
    }
    final int thisDay = now.getDayOfMonth();
    final int thisMonth = now.getMonthValue();
    final int thisYear = now.getYear();

    final QDateDimension dateDimension = QDateDimension.dateDimension;

    //@formatter:off
    return getQueryFactory().select(dateDimension)
                            .from(dateDimension)
                            .where(dateDimension.dayOfMonth.eq(thisDay)
                             .and(dateDimension.month.eq(thisMonth)
                             .and(dateDimension.year.eq(thisYear))))
                            .fetchOne();
    //@formatter:on
  }

  /**
   * given today &amp; an already constructed {@link PeriodCategory}, assert if the period (that the periodCategory represents) is current or in arrears
   *
   * @param today
   * @param periodCategory
   * @return
   */
  public PeriodCategory decidePeriodCategory(DateDimension today, PeriodCategory periodCategory) {

    if (today.getIsoYear().intValue() == periodCategory.getYear().intValue()) {
      if ((today.getMonth().intValue() - periodCategory.getMonth().intValue()) == 1) {
        // declaration is for last month
        periodCategory.setCurrent(true);
      } else {
        periodCategory.setArrear(true);
      }
    } else if ((today.getIsoYear().intValue() - periodCategory.getYear().intValue()) == 1) {
      if ((periodCategory.getMonth().intValue() - today.getMonth().intValue()) == 11) {
        // declaration is for last month of the previous year
        periodCategory.setCurrent(true);
      } else {
        periodCategory.setArrear(true);
      }
    } else {
      // report declaration has been made for more than an year older
    }
    return periodCategory;

  }

  private Integer extractMonthOrWeekFromPeriod(String period) {

    Preconditions.checkArgument(StringUtils.isNotBlank(period), "blank period string can not be parsed");
    Preconditions.checkArgument(period.length() == 8);

    final Period parsed = Period.parse(period);
    final String month = parsed.getValue().substring(5, 7);
    return month.startsWith("0") ? Integer.parseInt(month.substring(1)) : Integer.parseInt(month);
  }

  /**
   * finds all distinct months for a given quarter
   *
   * @param quarter
   * @return corresponding months
   */
  public List<Period> fetchMonthsForQuarter(final Period quarter) {

    final QDateDimension dateDimension = QDateDimension.dateDimension;

    // @formatter:off
    final List<DateDimension> months = getQueryFactory()
                                .select(dateDimension)
                                .from(dateDimension)
                                .where(dateDimension.yearQuarter.eq(quarter.getValue()))
                                .fetch()
                                .stream()
                                .filter(DateDimension.distinctByKey(DateDimension::getYearMonth))
                                .collect(Collectors.toList());
    //@formatter:on
    return months.stream().map(new Function<DateDimension, Period>() {
      @Override
      public Period apply(DateDimension t) {

        return new Period(t.getYearMonth(), PeriodType.M);
      }
    }).collect(Collectors.toList());

  }

  /**
   * Fetch a date dimension representing the current date in the scheme's time zone
   *
   * @return date dimension entry that represents today, for default zone id
   */
  public DateDimension fetchToday(Scheme scheme) {

    ZoneId zoneId;
    if (scheme != null) {
      zoneId = scheme.getTimeZoneId();
    } else {
      zoneId = defaultZoneId();
    }
    return fetchToday(zoneId);
  }

  public static ZoneId defaultZoneId() {

    return ZoneId.of(DEFAULT_ZONE_ID);
  }

  /**
   * fetch today based on a specific arguments
   *
   * @param dd
   *          date
   * @param mm
   *          month
   * @param yyyy
   *          year
   * @param zoneId
   *          zone id, default zone id being "Australia/Sydney"
   * @return
   */
  public DateDimension fetchToday(String dd, String mm, String yyyy, String zoneId) {

    final LocalDateTime now = getNow(dd, mm, yyyy, zoneId);
    return correspondingDateDimension(now);
  }

  // public DateDimension asDate(LocalDate date) {
  // //@format:off
  // final DateDimension dateDimension = fetchToday(padLeft(String.valueOf(date.getDayOfMonth()), 2, '0')
  // , padLeft(String.valueOf(date.getMonth()), 2, '0')
  // , String.valueOf(date.getYear())
  // , DEFAULT_ZONE_ID);
  // //@format:on
  // return dateDimension;
  // }

  /**
   * uses system clock with supplied zone id
   *
   * @param zoneId
   * @return date dimension entry that represents today, for supplied zone id
   */
  public DateDimension fetchToday(ZoneId zoneId) {

    final LocalDateTime now = getNow(zoneId);
    return correspondingDateDimension(now);
  }

  /**
   * given period type, find the relevant period w.r.t provided date dimension
   *
   * @param dateDimension
   *          representing today
   * @param periodType
   *          see {@link PeriodType}
   * @return {@link Period} representing last quarter as Q2018-5; last month as Q2018-12; last week as W2018-45
   */
  public Period findPaymentPeriod(DateDimension dateDimension, PeriodType periodType) {

    checkArgument(dateDimension != null);
    checkArgument(periodType != null);

    Period period = null;
    String internal = "";
    int year = 0;

    final String dateInYYYYMMDDFormat = dateDimension.getDateInYYYYMMDDFormat();
    final LocalDate today = LocalDate.parse(dateInYYYYMMDDFormat);

    switch (periodType) {
    case Q:
      internal = today.query(new PreviousQuarter());
      period = new Period(internal, periodType);

      // TODO decide period start and end date if required

      break;

    case M:
      final LocalDate previousMonth = today.minusMonths(1);
      final LocalDate start = previousMonth.withDayOfMonth(1);
      final LocalDate end = previousMonth.withDayOfMonth(previousMonth.lengthOfMonth());

      final int month = previousMonth.getMonthValue();
      year = previousMonth.getYear();

      internal = String.join("-", valueOf(year), padLeft(valueOf(month), 2, '0'));
      period = new Period(internal, periodType);

      period.setStart(start);
      period.setEnd(end);
      break;

    case W:
      final LocalDate previousWeek = today.minusDays(7);
      year = previousWeek.getYear();
      final WeekFields weekFields = WeekFields.of(Locale.ENGLISH);
      final int weekNumber = previousWeek.get(weekFields.weekOfWeekBasedYear());
      internal = String.join("-", valueOf(year), padLeft(valueOf(weekNumber), 2, '0'));
      period = new Period(internal, periodType);
      break;

    case D:
      final LocalDate previousDay = today.minusDays(1);
      internal = previousDay.toString();
      period = new Period(internal, periodType);
      break;

    default:
      break;
    }

    return period;
  }

  private LocalDateTime getNow(String dd, String mm, String yyyy, String zomeId) {

    return LocalDateTime.now(buildClock(dd, mm, yyyy, zomeId));
  }

  private static LocalDateTime getNow(ZoneId zoneId) {

    return LocalDateTime.now(buildClock(zoneId));
  }

  public static LocalDateTime now() {

    return getNow(defaultZoneId());
  }

  /**
   * get this moment in time
   *
   * @return this moment for the default zone id
   */
  public LocalDateTime getNow() {

    return getNow(defaultZoneId());
  }

  public JPAQueryFactory getQueryFactory() {

    final JPAQueryFactory factory = new JPAQueryFactory(em);
    return factory;
  }

  public String padLeft(String s, int size, char pad) {

    StringBuilder builder = new StringBuilder(s);
    builder = builder.reverse(); // reverse initial string
    while (builder.length() < size) {
      builder.append(pad); // append at end
    }
    return builder.reverse().toString(); // reverse again!

  }

  /**
   * computes the month section from a period string;
   *
   * @param period
   *          represented as M2018-07
   * @return month
   */
  public Integer parseMonth(String period) {

    return extractMonthOrWeekFromPeriod(period);
  }

  /**
   * computes the week section from a period string
   *
   * @param period
   *          represented as W2018-07
   * @return week
   */
  public Integer parseWeek(String period) {

    return extractMonthOrWeekFromPeriod(period);
  }

  /**
   * computes the year section from a period string; represented as M2018-09
   *
   * @param period
   * @return year in form of integer
   */
  public Integer parseYear(String period) {

    Preconditions.checkArgument(StringUtils.isNotBlank(period), "blank string can not be processed");
    Preconditions.checkArgument(period.length() >= 5);

    final Period parsed = Period.parse(period);
    final String year = parsed.getValue().substring(0, 4);
    return Integer.parseInt(year);

  }

  /**
   * Gets the current date and time
   * @return Returns the current date and time
   */
  public static Date currentDate() {
    return new Date();
  }

  /**
   * Returns a Calendar for the specified date using the specified scheme's timezone. This allows
   * to inspecting the current day, month, etc according to the scheme's timezone.
   * @param date The date & time to load into the calendar
   * @param scheme The scheme containing the timezone
   * @return Returns a Calendar at the specified date (and time) using the scheme's timezone
   */
  public static Calendar asCalendar(Date date, Scheme scheme) {
    final Calendar calendar = Calendar.getInstance();
    calendar.setTimeZone(TimeZone.getTimeZone(scheme.getTimeZoneId()));
    calendar.setTime(date);
    return calendar;
  }
  
  /**
   * Converts a Date into a ZonedDateTime with the time zone set for a particular scheme. This allows
   * to inspecting the current day, month, etc according to the scheme's timezone.
   * @param date
   * @param scheme
   * @return Returns the date & time with the time zone set to the scheme's time zone.
   */
  public static ZonedDateTime inSchemeTimezone(Date date, Scheme scheme) {    
    return ZonedDateTime.from(date.toInstant().atZone(scheme.getTimeZoneId()));
  }
  
  /**
   * Methods returns the previous quarter based on the date passed
   */
  public static class PreviousQuarter implements TemporalQuery<String> {

    @Override
    public String queryFrom(TemporalAccessor date) {

      final int thisMonth = date.get(ChronoField.MONTH_OF_YEAR);
      final int thisYear = date.get(ChronoField.YEAR);

      if (thisMonth <= 3) {
        return String.join("-", valueOf((thisYear - 1)), valueOf(new Integer(4)));
      } else if (thisMonth <= 6) {
        return String.join("-", valueOf((thisYear)), valueOf(new Integer(1)));
      } else if (thisMonth <= 9) {
        return String.join("-", valueOf((thisYear)), valueOf(new Integer(2)));
      } else {
        return String.join("-", valueOf((thisYear)), valueOf(new Integer(3)));
      }
    }

  }

  /**
   * given a period, sets it's start and end dates
   *
   * @param period
   *          which will be modified,
   * @return period, with start and end dates set, if found
   */
  public Period setTerminalDates(Period period) {

    checkArgument(period != null);
    checkArgument(period.getType() != null);
    checkArgument(!period.getValue().isEmpty());

    final PeriodType type = period.getType();
    final QDateDimension qDateDimension = QDateDimension.dateDimension;

    BooleanExpression condition = null;
    switch (type) {
    case W:
      condition = qDateDimension.isoYearWeek.eq(period.getValue());
      break;
    case M:
      condition = qDateDimension.yearMonth.eq(period.getValue());
      break;
    case Q:
      condition = qDateDimension.yearQuarter.eq(period.getValue());
      break;
    case D:
      condition = qDateDimension.dateInYYYYMMDDFormat.eq(period.getValue());
      break;
    default:
      break;
    }

    final List<DateDimension> dates = getQueryFactory().select(qDateDimension).from(qDateDimension).where(condition).orderBy(qDateDimension.dateInJulianFormat.asc()).fetch();
    if ((dates != null) && !dates.isEmpty()) {
      final DateDimension firstForThePeriod = dates.get(0);
      final DateDimension lastForThePeriod = dates.get(dates.size() - 1);
      final LocalDate start = firstForThePeriod.getLocalDate();
      final LocalDate end = lastForThePeriod.getLocalDate();
      period.setStart(start);
      period.setEnd(end);
    }
    return period;
  }

  public DateDimension getToday(Scheme scheme) {
    return fetchToday(scheme);
  }
 
  public Period periodFactory(String value, PeriodType type) {

    final Period period = new Period(value, type);
    setTerminalDates(period);
    if (type == PeriodType.Q) {
      final List<Period> monthsWithoutTerminalDates = fetchMonthsForQuarter(period);
      final List<Period> months = monthsWithoutTerminalDates.stream().map(new Function<Period, Period>() {

        @Override
        public Period apply(Period t) {

          return periodFactory(t.getValue(), t.getType());
        }
      }).collect(Collectors.toList());
      period.setPeriods(months);
    }
    return period;
  }

  public LocalDate getBusinessDay(LocalDate startDate, int day) {

    int businessDayCount = 0;
    LocalDate targetBusinessDate = startDate;
    String dayOfStartDate = startDate.getDayOfWeek().name();
    if (!(dayOfStartDate.equals("SATURDAY") || dayOfStartDate.equals("SUNDAY"))) {
      businessDayCount++;
    }
    while (businessDayCount < 10) {
      switch (targetBusinessDate.getDayOfWeek().name()) {
      case "FRIDAY": {
        targetBusinessDate = targetBusinessDate.plusDays(3);
        businessDayCount++;
        break;
      }
      case "SATURDAY": {
        targetBusinessDate = targetBusinessDate.plusDays(2);
        break;
      }
      default: {
        targetBusinessDate = targetBusinessDate.plusDays(1);
        businessDayCount++;
      }
      }
    }
    return targetBusinessDate;
  }

  /**
   * Gets yesterday's date at 11:59pm (one nano second less than the start of the current day) in a particular scheme's timezone.
   * @param scheme If not null, the time/date will be adjusted based on this scheme's timezone. 
   * @return Returns yesterday's date with the time set to 11:59pm (one nano second less than the start of the current day). 
   */
  public static Date getTodayMinusOne(Scheme scheme) {
    LocalDate today = LocalDate.now();
    
    ZonedDateTime startOfTodayForScheme;
    if (scheme != null) {
      startOfTodayForScheme = today.atStartOfDay(scheme.getTimeZoneId());
    } else {
      startOfTodayForScheme = today.atStartOfDay(ZoneId.of("UTC"));
    }
    
    ZonedDateTime endOfPreviousDay = startOfTodayForScheme.minusNanos(1);
    return Date.from(endOfPreviousDay.toInstant());
  }

  /**
   * Gets today's date at the start of the day in a particular scheme's timezone.
   * @param scheme If not null, the time/date will be adjusted based on this scheme's timezone. 
   * @return Returns today's date with the time set to 12:00am in the scheme's timezone
   */
  public static Date getStartOfDay(Scheme scheme) {
    LocalDate today = LocalDate.now();
    
    ZonedDateTime startOfTodayForScheme;
    if (scheme != null) {
      startOfTodayForScheme = today.atStartOfDay(scheme.getTimeZoneId());
    } else {
      startOfTodayForScheme = today.atStartOfDay(ZoneId.of("UTC"));
    }
    return Date.from(startOfTodayForScheme.toInstant());
  }

  public static Date getDateMinusOne(Date date) {
    SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
    try {
      Date dateWithoutTime = sdf.parse(sdf.format((date)));
      Calendar cal = Calendar.getInstance();
      cal.setTime(dateWithoutTime);
      cal.add(Calendar.DATE, -1);
      cal.set(Calendar.HOUR, 11);
      cal.set(Calendar.MINUTE, 59);
      cal.set(Calendar.AM_PM, 1);
      return cal.getTime();
    } catch (ParseException e) {
      throw new CoexRuntimeException("COEX1026", null, e.getMessage());
    }
  }

  /**
   * Returns the date 1/1/1970. THe purpose of this is return a 
   * date which can be used as a minimum date in a date comparison. 
   * @return Returns the date
   */
  public static Date getMinimumDate() {
    Calendar cal = Calendar.getInstance();
    cal.set(Calendar.HOUR_OF_DAY, 12);
    cal.set(Calendar.MINUTE, 0);
    cal.set(Calendar.SECOND, 0);
    cal.set(Calendar.MILLISECOND, 0);
    cal.set(Calendar.YEAR, 1970);
    cal.set(Calendar.MONTH, 1);
    cal.set(Calendar.DAY_OF_MONTH, 1);
    return cal.getTime();
  }

  /**
   * Returns the date 1/1/2070. THe purpose of this is return a 
   * date which can be used as a maximum date in a date comparison. 
   * @return Returns the date
   */
  public static Date getMaximumDate() {
    Calendar cal = Calendar.getInstance();
    cal.set(Calendar.HOUR_OF_DAY, 12);
    cal.set(Calendar.MINUTE, 0);
    cal.set(Calendar.SECOND, 0);
    cal.set(Calendar.MILLISECOND, 0);
    cal.set(Calendar.YEAR, 2070);
    cal.set(Calendar.MONTH, 1);
    cal.set(Calendar.DAY_OF_MONTH, 1);
    return cal.getTime();
  }

  /**
   * Translates a scheme's local date (and time) into the equivalent within the UTC time zone
   * @param schemesLocalDateTime A date and time which is assumed to be in the same time zone as the scheme.
   * @param scheme The scheme's timezone will be used to translate the date & time.
   * @return Returns a translated date & time
   */
  public static ZonedDateTime translateToUtc(LocalDateTime schemesLocalDateTime, Scheme scheme) {
    return schemesLocalDateTime.atZone(scheme.getTimeZoneId());
  }

  /**
   * Gets the start of the day in a particular scheme's timezone (for comparing with other dates)
   * @param day The day
   * @param scheme The timezone will be set based on this
   * @returns Returns the start of the day in the scheme's timezone
   */
  public static ZonedDateTime getStartOfDay(LocalDate day, Scheme scheme) {
    return day.atStartOfDay().atZone(scheme.getTimeZoneId());
  }
  
  /**
   * Gets the start of the day in a particular scheme's timezone (for comparing with other dates)
   * @param day The day
   * @param scheme The timezone will be set based on this
   * @returns Returns the start of the day in the scheme's timezone
   */
  public static Date getStartOfDate(LocalDate day, Scheme scheme) {
    return Date.from(day.atStartOfDay().atZone(scheme.getTimeZoneId()).toInstant());
  }
  
}