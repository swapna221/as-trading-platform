package com.trading.manualorderservice.optionfilter;

import com.trading.manualorderservice.marketfeed.IndexPollingService;
import com.trading.manualorderservice.service.DhanAllApis;
import com.trading.manualorderservice.stockfilter.DhanStockHelper;
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

	/** Inject via constructor */
	private final DhanStockHelper dhanStockHelper;

	/** Option chain grouped by underlying */
	private final Map<String, List<OptionRow>> optionsByUnderlying = new HashMap<>();

	/** Spot Security IDs */
	private final Map<String, String> spotSecurityIds = new HashMap<>();

	private static final DateTimeFormatter MONTH_FMT =
			new DateTimeFormatterBuilder()
					.parseCaseInsensitive()
					.appendPattern("MMM-uuuu")
					.toFormatter(Locale.ENGLISH);

	/**
	 * 🔥 THIS RUNS AFTER SPRING CREATES THE BEAN
	 * and LOADS CSV CORRECTLY
	 */
	@PostConstruct
	public void init() throws Exception {
		LOGGER.info("Loading Option Master CSV...");

		List<Map<String, String>> csvRows = DhanOptionHelper.loadMasterCsv();

		int skipped = 0;

		for (Map<String, String> row : csvRows) {
			try {
				String instrType = row.getOrDefault("SEM_EXCH_INSTRUMENT_TYPE", "").trim().toUpperCase();
				String tradingSymbol = row.getOrDefault("SEM_TRADING_SYMBOL", "").trim();

				if (tradingSymbol.isEmpty()) {
					skipped++;
					continue;
				}

				if (instrType.startsWith("OP")) {
					String underlyingKey = extractUnderlying(tradingSymbol);
					OptionRow or = new OptionRow(row, underlyingKey);

					optionsByUnderlying.computeIfAbsent(underlyingKey, k -> new ArrayList<>()).add(or);

				} else if ("ES".equals(instrType) || "INDEX".equals(instrType)) {
					String underlyingKey = tradingSymbol.toUpperCase();
					String secId = row.getOrDefault("SEM_SMST_SECURITY_ID", "").trim();

					if (!secId.isEmpty()) {
						spotSecurityIds.putIfAbsent(underlyingKey, secId);
					}

				} else {
					skipped++;
				}

			} catch (Exception ignored) {
				skipped++;
			}
		}

		// Sort by expiry inside each underlying
		optionsByUnderlying.values().forEach(list ->
				list.sort(Comparator.comparing(o -> o.expiry, Comparator.nullsLast(LocalDate::compareTo)))
		);

		LOGGER.info("Option Master Loaded: {} underlyings, {} skipped rows",
				optionsByUnderlying.size(), skipped);
	}

	private String extractUnderlying(String tradingSymbol) {
		int dash = tradingSymbol.indexOf('-');
		return (dash > 0 ? tradingSymbol.substring(0, dash) : tradingSymbol).toUpperCase();
	}

	// ========================== Public APIs ==========================

	public boolean isValidUnderlying(String u) {
		return optionsByUnderlying.containsKey(u.toUpperCase());
	}

	public Optional<String> getUnderlyingSpotSecurityId(String u) {
		return Optional.ofNullable(spotSecurityIds.get(u.toUpperCase()));
	}

	public Optional<String> getEquitySecurityId(String u) {
		return getUnderlyingSpotSecurityId(u);
	}

	// ================= Option Order Builder ===========================

	public Map<String, Object> buildOrder(
			String underlying,
			String optionType,
			String moneyness,
			String transactionType,
			String tradeType,
			String placeOrderType,
			int numberOfLots,
			double spotPrice,
			String expiryMonthYear) {

		String u = underlying.toUpperCase();
		String type = optionType.toUpperCase();
		String money = moneyness.toUpperCase();

		List<OptionRow> all = optionsByUnderlying.getOrDefault(u, Collections.emptyList());

		if (all.isEmpty()) {
			throw new RuntimeException("No options found for underlying: " + underlying);
		}

		// Filter CE/PE
		List<OptionRow> filtered = all.stream()
				.filter(r -> r.optionType.equalsIgnoreCase(type))
				.toList();

		if (filtered.isEmpty()) {
			throw new RuntimeException("No " + type + " options for " + u);
		}

		YearMonth targetMonth = YearMonth.parse(expiryMonthYear.trim(), MONTH_FMT);

		List<OptionRow> monthRows = filtered.stream()
				.filter(r -> r.expiry != null && YearMonth.from(r.expiry).equals(targetMonth))
				.filter(r -> r.expiryFlag == null || r.expiryFlag.isBlank() || r.expiryFlag.equalsIgnoreCase("M"))
				.toList();

		if (monthRows.isEmpty()) {
			throw new RuntimeException("No monthly options for " + u);
		}

		LocalDate lastExpiry = monthRows.stream()
				.map(r -> r.expiry)
				.max(LocalDate::compareTo)
				.orElseThrow();

		List<OptionRow> expiryRows = monthRows.stream()
				.filter(r -> r.expiry.equals(lastExpiry))
				.toList();

		double step = getStrikeStep(expiryRows);
		double baseStrike = Math.round(spotPrice / step) * step;
		double targetStrike = adjustStrike(baseStrike, step, type, money);

		OptionRow chosen = expiryRows.stream()
				.min(Comparator.comparingDouble(r -> Math.abs(r.strike - targetStrike)))
				.orElseThrow();

		Map<String, Object> order = new LinkedHashMap<>();
		order.put("security_id", chosen.securityId);
		order.put("exchange_segment", "NSE_FNO");
		order.put("transaction_type", transactionType);
		order.put("quantity", chosen.lotSize * numberOfLots);
		order.put("order_type", "MARKET");
		order.put("product_type", tradeType);
		order.put("price", 0.0);
		order.put("trigger_price", 0.0);
		order.put("stock_name", underlying);
		order.put("trading_symbol", chosen.tradingSymbol);
		order.put("custom_Symbol", chosen.customSymbol);
		order.put("place_order_type", placeOrderType);
		order.put("job_status", "NEW");

		return order;
	}

	// ===================== Utilities ==========================

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

	public double resolveSpotPrice(String underlying,
								   IndexPollingService indexSvc,
								   DhanAllApis dhanApis,
								   BrokerUserDetails creds,
								   Long userId) throws Exception {

		String u = underlying.toUpperCase();

		return switch (u) {
			case "NIFTY", "NIFTY50" -> Optional.ofNullable(indexSvc.getNiftySpot())
					.orElseThrow(() -> new RuntimeException("NIFTY Spot not available"));

			case "BANKNIFTY", "NIFTY_BANK" -> Optional.ofNullable(indexSvc.getBankNiftySpot())
					.orElseThrow(() -> new RuntimeException("BANKNIFTY Spot not available"));

			default -> {
				String secId = dhanStockHelper.getSecurityId(u)
						.orElseThrow(() -> new RuntimeException("Stock underlying missing: " + u));
				yield dhanApis.fetchLtpEquity(secId, creds, userId);
			}
		};
	}
}
