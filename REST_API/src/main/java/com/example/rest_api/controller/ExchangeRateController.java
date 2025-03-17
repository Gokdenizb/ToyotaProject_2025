package com.example.rest_api.controller;

import com.example.rest_api.Service.TcpClientService;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/rates")
public class ExchangeRateController {
    private final TcpClientService tcpClientService;

    public ExchangeRateController(TcpClientService tcpClientService){
        this.tcpClientService = tcpClientService;
    }

    @GetMapping("/{currencyPair}")
    public String getExchangeRates(@PathVariable String currencyPair){
        return tcpClientService.getExchangeRates(currencyPair);
    }
}
