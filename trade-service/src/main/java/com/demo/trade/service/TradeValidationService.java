package com.demo.trade.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TradeValidationService {

    @Value("${app.supported-symbols}")
    private List<String> supportedSymbols;

    public void validateSymbol(String symbol) {
        if (!supportedSymbols.contains(symbol)) {
            throw new IllegalArgumentException("Unsupported symbol: " + symbol +
                    ". Supported: " + supportedSymbols);
        }
    }

    public void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }
    }
}
