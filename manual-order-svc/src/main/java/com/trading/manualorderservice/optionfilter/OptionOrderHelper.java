package com.trading.manualorderservice.optionfilter;

import com.trading.manualorderservice.market.LtpServiceHolder;
import com.trading.manualorderservice.marketfeed.IndexPollingService;
import com.trading.manualorderservice.service.DhanAllApis;
import com.trading.manualorderservice.stockfilter.DhanStockHelper;
import com.trading.manualorderservice.util.BlackScholesUtil;
import com.trading.shareddto.entity.BrokerUserDetails;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OptionOrderHelper {

	private static final Logger LOGGER = LoggerFactory.getLogger(OptionOrderHelper.class);

	/** Helpers */
	private final DhanStockHelper dhanStockHelper;

	/** Loaded option master grouped by underlying */
	private final Map<String, List<OptionRow>> optionsByUnderlying = new HashMap<>();

	/** Underlying → Security ID (for equities and index spot) */
	private final Map<String, String> spotSecurityIds = new HashMap<>();

	private static final DateTimeFormatter MONTH_FMT =
			new DateTimeFormatterBuilder()
					.parseCaseInsensitive()
					.appendPattern("MMM-uuuu")
					.toFormatter(Locale.ENGLISH);

	// ============================================================
	//                 LOAD OPTION MASTER CSV
	// ============================================================

	@PostConstruct
	public void init() throws Exception {
		LOGGER.info("Loading Option Master CSV...");

		List<Map<String, String>> rows = DhanOptionHelper.loadMasterCsv();
		int skipped = 0;

		for (Map<String, String> row : rows) {
			try {
				String instrType = row.getOrDefault("SEM_EXCH_INSTRUMENT_TYPE", "").trim().toUpperCase();
				String tradingSymbol = row.getOrDefault("SEM_TRADING_SYMBOL", "").trim();

				if (tradingSymbol.isEmpty()) {
					skipped++;
					continue;
				}

				// Options
				if (instrType.startsWith("OP")) {
					String underlying = extractUnderlying(tradingSymbol);
					OptionRow or = new OptionRow(row, underlying);
					optionsByUnderlying.computeIfAbsent(underlying, k -> new ArrayList<>()).add(or);
				}
				// Equity Spot / Index Spot
				else if (instrType.equals("ES") || instrType.equals("INDEX")) {
					String underlyingKey = tradingSymbol.toUpperCase();
					String secId = row.getOrDefault("SEM_SMST_SECURITY_ID", "").trim();
					if (!secId.isEmpty()) {
						spotSecurityIds.putIfAbsent(underlyingKey, secId);
					}
				} else {
					skipped++;
				}

			} catch (Exception ex) {
				skipped++;
			}
		}

		// Sort options inside each underlying by expiry date
		optionsByUnderlying.values().forEach(list ->
				list.sort(Comparator.comparing(o -> o.expiry, Comparator.nullsLast(LocalDate::compareTo))));

		LOGGER.info("Option Master Loaded: {} underlyings, {} skipped rows",
				optionsByUnderlying.size(), skipped);
	}

	private String extractUnderlying(String symbol) {
		int dash = symbol.indexOf('-');
		return (dash > 0 ? symbol.substring(0, dash) : symbol).toUpperCase();
	}

	// ============================================================
	//                  OPTION ORDER BUILDER
	// ============================================================

	public Map<String, Object> buildOrder(
			String underlying,
			String optionType,
			String moneyness,
			String transactionType,
			String tradeType,
			String placeOrderType,
			int numberOfLots,
			double spotPrice,
			String expiryMonthYear
	) {

		String u = underlying.toUpperCase();
		List<OptionRow> all = optionsByUnderlying.getOrDefault(u, Collections.emptyList());

		if (all.isEmpty()) {
			throw new RuntimeException("No options found for underlying: " + u);
		}

		String type = optionType.toUpperCase();
		String money = moneyness.toUpperCase();

		// Filter CE / PE
		List<OptionRow> filtered = all.stream()
				.filter(r -> r.optionType.equalsIgnoreCase(type))
				.collect(Collectors.toList());

		if (filtered.isEmpty()) {
			throw new RuntimeException("No " + type + " options for " + u);
		}

		// Filter only rows of target expiry month
		YearMonth targetMonth = YearMonth.parse(expiryMonthYear.trim(), MONTH_FMT);

		List<OptionRow> monthly = filtered.stream()
				.filter(r -> r.expiry != null && YearMonth.from(r.expiry).equals(targetMonth))
				.filter(r -> r.expiryFlag == null || r.expiryFlag.isBlank() || r.expiryFlag.equalsIgnoreCase("M"))
				.toList();

		if (monthly.isEmpty()) {
			throw new RuntimeException("No monthly expiry rows for " + u);
		}

		// Find latest expiry of that month
		LocalDate lastExpiry = monthly.stream()
				.map(r -> r.expiry)
				.max(LocalDate::compareTo)
				.orElseThrow();

		List<OptionRow> expiryRows = monthly.stream()
				.filter(r -> r.expiry.equals(lastExpiry))
				.toList();

		// Strike selection
		double step = getStrikeStep(expiryRows);
		double base = Math.round(spotPrice / step) * step;
		double targetStrike = adjustStrike(base, step, type, money);

		OptionRow chosen = expiryRows.stream()
				.min(Comparator.comparingDouble(r -> Math.abs(r.strike - targetStrike)))
				.orElseThrow();

		Map<String, Object> out = new LinkedHashMap<>();
		out.put("security_id", chosen.securityId);
		out.put("exchange_segment", "NSE_FNO");
		out.put("transaction_type", transactionType);
		out.put("quantity", chosen.lotSize * numberOfLots);
		out.put("order_type", "MARKET");
		out.put("product_type", tradeType);
		out.put("price", 0.0);
		out.put("trigger_price", 0.0);
		out.put("trading_symbol", chosen.tradingSymbol);
		out.put("stock_name", u);
		out.put("custom_Symbol", chosen.customSymbol);
		out.put("place_order_type", placeOrderType);
		out.put("job_status", "NEW");

		return out;
	}

	// ============================================================
	//                     SPOT PRICE RESOLUTION
	// ============================================================

	/**
	 * Centralized & Correct Spot Price Resolver
	 * Works for:
	 *   ✔ NIFTY
	 *   ✔ BANKNIFTY
	 *   ✔ Equity Underlyings (SBIN, TCS, etc.)
	 */
	public double resolveSpotPrice(
			String underlying,
			IndexPollingService indexSvc,
			DhanAllApis dhanApis,
			BrokerUserDetails creds,
			Long userId
	) throws Exception {

		String u = underlying.toUpperCase().replace("_", "").trim();

		// --------------------------------------------
		// 1️⃣ INDEX UNDERLYINGS
		// --------------------------------------------

		if (u.equals("NIFTY") || u.equals("NIFTY50")) {

			Double val = indexSvc.getNiftySpot();
			if (val == null) {
				Thread.sleep(200);
				val = indexSvc.getNiftySpot();
			}
			if (val != null) return val;

			return dhanApis.fetchLtpIndex("NIFTY", creds, userId);
		}

		if (u.equals("BANKNIFTY") || u.equals("NIFTYBANK")) {

			Double val = indexSvc.getBankNiftySpot();
			if (val == null) {
				Thread.sleep(200);
				val = indexSvc.getBankNiftySpot();
			}
			if (val != null) return val;

			return dhanApis.fetchLtpIndex("BANKNIFTY", creds, userId);
		}

		// --------------------------------------------
		// 2️⃣ EQUITY UNDERLYINGS (SBIN, TCS, RELIANCE)
		// --------------------------------------------

		Optional<String> eq = dhanStockHelper.getSecurityId(u);
		if (eq.isEmpty()) {
			throw new RuntimeException("Unknown equity underlying: " + u);
		}

		String secId = eq.get();

		// Use central LTP service (cache → batch → fallback)
		return LtpServiceHolder.get()
				.getLtpForTrading(secId, "NSE_EQ", creds, userId);
	}

	// ============================================================
	//                    Utility Functions
	// ============================================================

	private double getStrikeStep(List<OptionRow> rows) {
		List<Double> strikes = rows.stream().map(r -> r.strike).distinct().sorted().toList();
		return strikes.size() > 1 ? strikes.get(1) - strikes.get(0) : 50;
	}

	private double adjustStrike(double base, double step, String type, String money) {
		boolean ce = type.equalsIgnoreCase("CE");
		return switch (money) {
			case "OTM" -> ce ? base + step : base - step;
			case "ITM" -> ce ? base - step : base + step;
			default -> base;
		};
	}

	public boolean isValidUnderlying(String u) {
		return optionsByUnderlying.containsKey(u.toUpperCase());
	}

	public Optional<String> getUnderlyingSpotSecurityId(String u) {
		return Optional.ofNullable(spotSecurityIds.get(u.toUpperCase()));
	}

	public double computeIvForOption(
			OptionRow row,
			double spotPrice,
			double optionLtp
	) {
		boolean isCall = row.optionType.equalsIgnoreCase("CE");
		double strike = row.strike;

		long days = java.time.temporal.ChronoUnit.DAYS.between(
				LocalDate.now(), row.expiry);
		double T = Math.max(days / 365.0, 1.0 / 365.0); // maturity in years

		return BlackScholesUtil.computeIV(isCall, spotPrice, strike, optionLtp, T);
	}

	public List<OptionRow> getOptions(String underlying) {
		return optionsByUnderlying.getOrDefault(underlying.toUpperCase(), List.of());
	}

	public static DateTimeFormatter getMonthFormatter() {
		return MONTH_FMT;
	}



}
