package com.trading.shareddto.shareddto;



import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderEvent {
    private Long userId;
    private String underlying;
    private String optionType;
    private String moneyness;
    private String transactionType;
    private String tradeType;
    private String placeOrderType;
    private int numberOfLots;
    private String dateInMonthYear;
}
