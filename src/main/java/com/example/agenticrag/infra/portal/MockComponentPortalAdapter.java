package com.example.agenticrag.infra.portal;

import com.example.agenticrag.domain.model.ComponentDetails;
import com.example.agenticrag.domain.model.ComponentEndpoint;
import com.example.agenticrag.domain.port.ComponentPortalPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Adapter FAKE do portal, ativado pelo perfil {@code mock}.
 * Permite exercitar toda a Fase 1 (ingestão -> recuperação -> aprovação) sem o portal real.
 */
@Component
@Profile("mock")
public class MockComponentPortalAdapter implements ComponentPortalPort {

    private static final ComponentDetails PAYMENTS_SDK = new ComponentDetails(
            "payments-sdk",
            "Payments SDK",
            "2.3.1",
            "SDK de pagamentos: criação de cobranças, consulta de status e estorno.",
            List.of("pagamentos", "rest", "idempotente"),
            "https://git.example.com/platform/payments-sdk",
            List.of(
                    new ComponentEndpoint("POST", "/v2/charges",
                            "Cria uma cobrança. Requer header Idempotency-Key.", true,
                            "{ \"amount\": 1000, \"currency\": \"BRL\", \"customerId\": \"cus_123\" }",
                            "{ \"id\": \"chg_abc\", \"status\": \"PENDING\" }"),
                    new ComponentEndpoint("GET", "/v2/charges/{id}",
                            "Consulta o status de uma cobrança.", true,
                            "path param: id (string)",
                            "{ \"id\": \"chg_abc\", \"status\": \"PAID\" }"),
                    new ComponentEndpoint("POST", "/v2/charges/{id}/refund",
                            "Estorna total ou parcialmente uma cobrança.", true,
                            "{ \"amount\": 1000 }",
                            "{ \"id\": \"ref_xyz\", \"status\": \"REFUNDED\" }")));

    private static final String PAYMENTS_README = """
            # Payments SDK

            SDK oficial de pagamentos da plataforma.

            ## Autenticação
            Todas as chamadas exigem `Authorization: Bearer <token>` obtido no portal do desenvolvedor.

            ## Idempotência
            `POST /v2/charges` exige o header `Idempotency-Key` (UUID). Reenvios com a mesma chave
            retornam a cobrança original, evitando cobranças duplicadas.

            ## Rate limiting
            100 req/s por token. Respostas 429 trazem `Retry-After` em segundos.

            ## Erros
            Padrão RFC 7807 (application/problem+json). Campos: `type`, `title`, `status`, `detail`.
            """;

    @Override
    public Optional<ComponentDetails> fetchComponent(String componentId) {
        return "payments-sdk".equals(componentId) ? Optional.of(PAYMENTS_SDK) : Optional.empty();
    }

    @Override
    public Optional<String> fetchReadme(String componentId) {
        return "payments-sdk".equals(componentId) ? Optional.of(PAYMENTS_README) : Optional.empty();
    }
}
