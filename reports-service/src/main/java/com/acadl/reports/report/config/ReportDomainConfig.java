package com.acadl.reports.report.config;

import com.acadl.reports.report.model.BalanceCalculator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registra os serviços de domínio (classes puras, sem anotações do Spring). */
@Configuration
public class ReportDomainConfig {

    @Bean
    public BalanceCalculator balanceCalculator() {
        return new BalanceCalculator();
    }
}
