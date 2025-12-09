package com.trading.manualorderservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IvResponseDto {
    private String option;
    private double iv;
}
