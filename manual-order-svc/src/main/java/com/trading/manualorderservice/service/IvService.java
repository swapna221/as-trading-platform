package com.trading.manualorderservice.service;

import com.trading.manualorderservice.dto.IvRequestDto;
import com.trading.manualorderservice.dto.IvResponseDto;
import com.trading.manualorderservice.optionfilter.OptionOrderHelper;
import com.trading.manualorderservice.optionfilter.OptionRow;
import com.trading.shareddto.entity.BrokerUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class IvService {

    private final OptionOrderHelper optionHelper;
    private final DhanCredentialService credentialService;
    private final DhanAllApis dhanApis;

    /**
     * Main entry point
     */
    public IvResponseDto fetchIv(IvRequestDto req) throws Exception {

        String underlying = req.getUnderlying().toUpperCase();
        String optionType = req.getOptionType().toUpperCase();
        String moneyness  = req.getMoneyness().toUpperCase();

        // Step 1 → get system credentials
        BrokerUserDetails system = credentialService.getSystemUser();

        // Step 2 → determine ATM strike based on latest spot
        double spot = determineSpot(underlying, system);

        // Step 3 → find matching option contract (CE/PE + ATM)
        OptionRow row = findOptionContract(underlying, optionType, moneyness, req.getDateInMonthYear(), spot);

        // Step 4 → call Dhan IV API
        double iv = dhanApis.fetchIv(row.securityId, system);

        return IvResponseDto.builder()
                .option(row.customSymbol)
                .iv(iv)
                .build();
    }

    private double determineSpot(String underlying, BrokerUserDetails system) throws Exception {
        return optionHelper.resolveSpotPrice(
                underlying,
                null,
                dhanApis,
                system,
                system.getUserId()
        );
    }

    private OptionRow findOptionContract(
            String underlying,
            String optionType,
            String moneyness,
            String expiryMonthYear,
            double spot
    ) {
        List<OptionRow> all = optionHelper.getOptions(underlying);

        List<OptionRow> filtered = all.stream()
                .filter(r -> r.optionType.equalsIgnoreCase(optionType))
                .toList();

        YearMonth ym = YearMonth.parse(
                expiryMonthYear.trim(),
                OptionOrderHelper.getMonthFormatter()
        );

        List<OptionRow> monthRows = filtered.stream()
                .filter(r -> r.expiry != null && YearMonth.from(r.expiry).equals(ym))
                .toList();

        double step = monthRows.get(1).strike - monthRows.get(0).strike;

        double atmStrike = Math.round(spot / step) * step;

        return monthRows.stream()
                .min(Comparator.comparingDouble(r -> Math.abs(r.strike - atmStrike)))
                .orElseThrow();
    }

}
