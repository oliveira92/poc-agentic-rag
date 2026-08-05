package com.example.agenticrag.security.dataset;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Os <b>cenários</b> em que o dataset de segurança é executado.
 *
 * <p>Um cenário é o que o controle precisa saber para julgar: qual arquivo de casos, o que a
 * base de conhecimento contém (insumo do juiz de escopo) e quem é o requisitante (insumo do
 * controle de autorização). É o mesmo par de insumos que a aplicação real monta a partir do
 * registro de ingestão e do usuário autenticado.
 *
 * <p>Há <b>dois</b> cenários, e a razão é substantiva: {@code componentes} é o domínio desta PoC
 * (o advisor de componentes, com o {@code payments-sdk} do perfil mock) e {@code a05-seguros} é
 * o dataset de atendimento em seguros da atividade A05. Os mesmos controles, com os mesmos
 * arquétipos de ataque, rodando em dois domínios sem uma linha de código específica de domínio —
 * o que um cenário só não conseguiria demonstrar.
 *
 * @param defaultScenario  cenário usado quando nenhum é informado
 * @param scenarios        cenários disponíveis
 */
@ConfigurationProperties(prefix = "app.security-dataset")
public record SecurityDatasetProperties(
        @DefaultValue("componentes") String defaultScenario,
        @DefaultValue List<Scenario> scenarios) {

    /**
     * @param name           id estável usado na API e nos testes
     * @param label          nome exibido no relatório
     * @param resource       caminho do CSV com os casos
     * @param summary        descrição do conteúdo ingerido — insumo do juiz de escopo
     * @param subjectId      identidade do requisitante (habilita o controle de autorização)
     * @param ownedResources recursos que o requisitante pode consultar
     */
    public record Scenario(
            String name,
            String label,
            String resource,
            @DefaultValue("") String summary,
            @DefaultValue("") String subjectId,
            @DefaultValue List<String> ownedResources) {
    }

    public Scenario primary() {
        return byName(defaultScenario);
    }

    public Scenario byName(String name) {
        if (scenarios == null || scenarios.isEmpty()) {
            throw new IllegalStateException("Nenhum cenário de dataset configurado em app.security-dataset.");
        }
        for (Scenario s : scenarios) {
            if (s.name().equalsIgnoreCase(name)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Cenário de dataset desconhecido: '" + name
                + "'. Disponíveis: " + scenarios.stream().map(Scenario::name).toList());
    }
}
