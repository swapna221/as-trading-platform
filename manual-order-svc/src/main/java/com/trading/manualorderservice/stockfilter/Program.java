//package com.trading.manualorderservice.stockfilter;
//
//public class Program {
//    public static void main(String[] args) throws Exception {
//        DhanStockHelper stockHelper = new DhanStockHelper();
//        String symbol = "SBIN";
//
//        System.out.println("Looking up: " + symbol);
//        stockHelper.getSecurityId(symbol)
//                .ifPresentOrElse(
//                        id -> System.out.println("✅ Security ID: " + id),
//                        () -> System.out.println("❌ Symbol not found")
//                );
//        stockHelper.getExchange(symbol).ifPresent(ex -> System.out.println("Exchange: " + ex));
//        stockHelper.getLotSize(symbol).ifPresent(lot -> System.out.println("Lot Size: " + lot));
//    }
//
//
//}
