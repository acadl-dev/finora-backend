package com.acadl.reports.report.client;

import com.acadl.reports.report.exception.TransactionSourceUnauthorizedException;
import com.acadl.reports.report.exception.TransactionSourceUnavailableException;
import com.acadl.reports.report.mapper.ReportEntryMapper;
import com.acadl.reports.report.model.ReportEntry;
import com.acadl.reports.report.model.TransactionSource;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adaptador da porta {@link TransactionSource}: busca as transações no finora e as
 * traduz para o modelo do contexto de Relatórios (Anti-Corruption Layer).
 */
@Component
@RequiredArgsConstructor
public class FinoraTransactionSource implements TransactionSource {

    private final FinoraTransactionClient client;

    @Override
    public List<ReportEntry> fetchEntries(String authorizationHeader) {
        List<FinoraTransactionResponse> transactions;
        try {
            transactions = client.listTransactions(authorizationHeader);
        } catch (FeignException.Unauthorized | FeignException.Forbidden e) {
            throw new TransactionSourceUnauthorizedException();
        } catch (FeignException e) {
            // inclui finora fora do ar, sem instância no Eureka (503) e timeouts
            throw new TransactionSourceUnavailableException(e);
        }

        if (transactions == null) {
            return List.of();
        }
        return transactions.stream()
                .map(ReportEntryMapper::toEntry)
                .toList();
    }
}
