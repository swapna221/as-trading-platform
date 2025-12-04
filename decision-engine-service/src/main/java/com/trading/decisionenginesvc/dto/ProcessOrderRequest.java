package com.trading.decisionenginesvc.dto;
import com.trading.shareddto.shareddto.OrderEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProcessOrderRequest {
    private OrderEvent order;
}
