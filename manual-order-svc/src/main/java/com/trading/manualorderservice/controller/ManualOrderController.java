package com.trading.manualorderservice.controller;

import com.trading.manualorderservice.service.DhanCredentialService;
import com.trading.manualorderservice.service.OrderBuildService;
import com.trading.shareddto.entity.BrokerUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/manual-order")
@Slf4j
public class ManualOrderController {

	private final DhanCredentialService dhanCredentialService;
	private final OrderBuildService orderBuildService;

	// inside ManualOrderController
	public record Req(
			String workflow,           // "EQUITY_INTRADAY" or "OPTION"

			// EQUITY fields
			String symbol,             // SBIN

			// OPTION fields
			String underlying,         // SBIN
			String optionType,         // CE/PE
			String moneyness,          // ATM/ITM/OTM
			Integer numberOfLots,
			Integer quantity,
			String dateInMonthYear,    // DEC-2025

			// Common
			String transactionType,    // BUY/SELL
			String tradeType,          // INTRADAY / DELIVERY
			String placeOrderType,     // MARKET / LIMIT

			// Risk params (for both equity & options)
			Double stoplossPercent,    // e.g. 1.0
			Double targetPercent,      // e.g. 2.0
			Double trailingPercent     // e.g. 1.0
	) {}



	@PostMapping("/buildProcess")
	public ResponseEntity<?> build(@RequestBody Req req, HttpServletRequest request) {
		try {
			Long userId = dhanCredentialService.extractUserIdFromRequest(request);
			BrokerUserDetails creds = dhanCredentialService.getDhanCredentials(userId, request);

			if (creds == null) {
				return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
						.body("Unable to fetch Dhan credentials");
			}

			log.info("Using Dhan credentials for User ID {}", userId);
			return ResponseEntity.ok(orderBuildService.buildOrder(req, creds,userId));

		} catch (SecurityException se) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(se.getMessage());
		} catch (Exception e) {
			log.error("Error processing order: ", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
		}
	}
}
