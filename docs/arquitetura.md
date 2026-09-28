# Arquitetura da solução

Este documento descreve o **domínio** (DDD), os **componentes**, o **fluxo das principais operações**
(diagramas de sequência), o **modelo de dados** e a **implantação** do Finora. Os diagramas usam
[Mermaid](https://mermaid.js.org/), que o GitHub desenha automaticamente ao abrir este arquivo.

**Sumário:**

1. [Domínio: subdomínios e bounded contexts](#1-domínio-subdomínios-e-bounded-contexts)
2. [Diagrama de componentes](#2-diagrama-de-componentes)
3. [Componentes internos de cada microsserviço](#3-componentes-internos-de-cada-microsserviço)
4. [Topologia de mensagens (RabbitMQ)](#4-topologia-de-mensagens-rabbitmq)
5. [Diagramas de sequência](#5-diagramas-de-sequência)
6. [Modelo de dados](#6-modelo-de-dados)
7. [Histórico de mudanças dos dados](#7-histórico-de-mudanças-dos-dados)
8. [Arquitetura orientada a eventos: prós e contras](#8-arquitetura-orientada-a-eventos-prós-e-contras)
9. [Diagrama de implantação (Kubernetes)](#9-diagrama-de-implantação-kubernetes)
10. [Evolução da arquitetura](#10-evolução-da-arquitetura)

---

## 1. Domínio: subdomínios e bounded contexts

O domínio do Finora é o **controle financeiro pessoal**: o usuário registra receitas e despesas e
acompanha o saldo por meio de relatórios.

| Subdomínio | Tipo | Por quê | Bounded context | Onde vive |
|---|---|---|---|---|
| **Transações** (receitas e despesas) | **Núcleo** (*core*) | É o motivo de o sistema existir; concentra as regras de negócio | Transações | `finora` (módulo `transaction`) |
| **Relatórios** (saldo, extrato em Excel) | **Suporte** (*supporting*) | Necessário para o negócio, mas deriva dos dados do núcleo | Relatórios | `reports-service` |
| **Identidade** (cadastro, login, JWT) | **Genérico** (*generic*) | Problema comum a qualquer sistema; sem regra específica do negócio | Identidade | `finora` (módulo `auth`) |

### Mapa de contextos

```mermaid
flowchart LR
    subgraph finora["Microsserviço finora"]
        ID["Identidade<br/>(genérico)"]
        TX["Transações<br/>(núcleo)"]
    end
    subgraph reports["Microsserviço reports-service"]
        ACL["Anti-Corruption Layer<br/>LedgerEntryMapper"]
        RP["Relatórios<br/>(suporte)"]
    end
    ID -->|"identifica o dono<br/>(JWT: id + e-mail)"| TX
    TX -->|"eventos publicados<br/>TransactionRegistered / TransactionRemoved"| ACL
    ACL -->|"traduz para o modelo local<br/>(LedgerEntry)"| RP
```

- **Transações → Relatórios** é uma relação **produtor/consumidor por eventos** (*published language*):
  o contexto de Transações publica fatos no RabbitMQ sem conhecer quem os consome.
- O contexto de Relatórios protege o próprio modelo com uma **Anti-Corruption Layer**
  (`LedgerEntryMapper`): a mensagem externa (`TransactionEventMessage`) vira um `LedgerEntry` e,
  na geração, um `ReportEntry`, conceitos do próprio contexto.
- Cada contexto tem **banco próprio** (`finora` e `finora_reports`): nenhum serviço lê a base do outro.

### Blocos táticos do DDD

| Conceito | Contexto de Transações (`finora`) | Contexto de Relatórios (`reports-service`) |
|---|---|---|
| Agregado (raiz) | `Transaction` (sem setters; criação só pela fábrica `Transaction.register`) | `FinancialReport` (ciclo `REQUESTED → READY / FAILED`, lock otimista `@Version`) |
| Value Objects | `Money` (positivo, 2 casas, igualdade por valor), `TransactionType` | `ReportPeriod`, `ReportSummary`, `ReportEntry`, `ReportFile` |
| Serviço de domínio | — | `BalanceCalculator` (receitas − despesas = saldo) |
| Eventos de domínio | `TransactionRegistered`, `TransactionRemoved` (interface `DomainEvent`) | `ReportRequested` |
| Portas (interfaces do domínio) | — | `TransactionSource`, `ReportExporter` |
| Adaptadores | `TransactionRepository` (Spring Data) | `LedgerTransactionSource`, `ExcelReportExporter` (Apache POI) |
| Serviço de aplicação | `TransactionService` | `ReportService`, `ReportGenerationService`, `LedgerProjectionService` |
| Exceções de domínio | `InvalidTransactionException`, `TransactionNotFoundException` | `InvalidReportPeriodException`, `ReportNotReadyException`, … |

---

## 2. Diagrama de componentes

```mermaid
flowchart TB
    user(["Usuário<br/>(navegador)"])

    subgraph front["finora-frontend (Next.js / React)"]
        ui["Páginas React<br/>(transações, relatórios)"]
        bff["Route handlers /api/*<br/>(BFF: sessão em cookie → Bearer JWT)"]
    end

    subgraph edge["Borda"]
        gw["gateway-service<br/>Spring Cloud Gateway :8080"]
        eureka["server-service<br/>Eureka :8761"]
    end

    subgraph core["Serviços de negócio"]
        fin["finora :8082<br/>Identidade + Transações<br/>(produtor de eventos)"]
        rep["reports-service :8083<br/>Relatórios<br/>(consumidor + workers)"]
    end

    subgraph data["Dados e mensageria"]
        pgf[("PostgreSQL<br/>finora")]
        pgr[("PostgreSQL<br/>finora_reports")]
        mq{{"RabbitMQ"}}
    end

    subgraph obs["Observabilidade"]
        loki["Loki (logs)"]
        tempo["Tempo (traces)"]
        prom["Prometheus (métricas)"]
        graf["Grafana"]
    end

    user --> ui --> bff -->|HTTP REST| gw
    gw -->|"/auth/**, /transactions/**"| fin
    gw -->|"/reports/**"| rep
    gw -.->|"descobre instâncias (lb://)"| eureka
    fin -.->|"registra-se"| eureka
    rep -.->|"registra-se"| eureka
    fin --> pgf
    rep --> pgr
    fin -->|"publica eventos (outbox relay)"| mq
    mq -->|"entrega eventos e comandos"| rep
    rep -->|"publica comando de relatório"| mq

    fin & rep & gw & eureka -.->|"logs"| loki
    fin & rep & gw -.->|"spans OTLP"| tempo
    prom -.->|"coleta /actuator/prometheus"| fin & rep & gw & eureka
    graf --> loki & tempo & prom
```

| Componente | Responsabilidade | Tecnologia |
|---|---|---|
| Front-end | Interface e BFF (guarda o JWT em cookie `httpOnly`, repassa como `Bearer`) | Next.js 16, React 19 |
| `gateway-service` | Porta única de entrada; roteia por caminho e balanceia entre réplicas | Spring Cloud Gateway (WebFlux) |
| `server-service` | Registro e descoberta de serviços | Spring Cloud Netflix Eureka |
| `finora` | Cadastro/login (JWT) e transações; grava eventos na outbox | Spring Boot, Spring Security, JPA, AMQP |
| `reports-service` | Projeção dos lançamentos, geração assíncrona do Excel, histórico de relatórios | Spring Boot, JPA, AMQP, Apache POI |
| RabbitMQ | Broker de eventos e comandos, com DLX/DLQ | RabbitMQ 4 |
| PostgreSQL (2) | Um banco por serviço (*database per service*) | PostgreSQL 13 |
| Loki / Tempo / Prometheus / Grafana | Logs, traces, métricas e painéis | Pilha Grafana LGTM |

> A autenticação é validada **em cada serviço de negócio** (filtro `JwtAuthenticationFilter`, mesma
> chave `JWT_SECRET`); o gateway apenas roteia.

---

## 3. Componentes internos de cada microsserviço

### finora — camadas e outbox

```mermaid
flowchart LR
    subgraph web["Camada de API"]
        AC["AuthController"]
        TC["TransactionController"]
        EH["*ExceptionHandler<br/>(@RestControllerAdvice)"]
    end
    subgraph app["Camada de aplicação"]
        CS["CredentialService"]
        TS["TransactionService<br/>@Transactional"]
        OR["OutboxEventRecorder"]
    end
    subgraph dom["Domínio"]
        T["Transaction (agregado)"]
        M["Money (VO)"]
        EV["TransactionRegistered<br/>TransactionRemoved"]
    end
    subgraph infra["Infraestrutura"]
        TR["TransactionRepository"]
        UR["UserRepository / CredentialRepository"]
        OER["OutboxEventRepository"]
        RL["OutboxRelay<br/>@Scheduled 1 s"]
    end
    TC --> TS --> T
    T --> M
    T --> EV
    TS --> TR
    TS --> OR --> OER
    AC --> CS --> UR
    RL --> OER
    RL -->|RabbitTemplate| MQ{{RabbitMQ}}
```

### reports-service — portas e adaptadores

```mermaid
flowchart LR
    subgraph inp["Adaptadores de entrada"]
        RC["ReportController (REST)"]
        TEL["TransactionEventsListener<br/>@RabbitListener"]
        GRL["GenerateReportCommandListener<br/>@RabbitListener"]
        SRD["StaleReportDispatcher<br/>@Scheduled"]
    end
    subgraph app["Aplicação"]
        LPS["LedgerProjectionService"]
        RS["ReportService"]
        RGS["ReportGenerationService"]
        RCP["ReportCommandPublisher<br/>(após o commit)"]
    end
    subgraph dom["Domínio"]
        FR["FinancialReport (agregado)"]
        BC["BalanceCalculator"]
        TSP["«porta» TransactionSource"]
        REP["«porta» ReportExporter"]
    end
    subgraph outp["Adaptadores de saída"]
        LTS["LedgerTransactionSource"]
        XLS["ExcelReportExporter"]
        REPO["Repositórios Spring Data"]
        MAP["LedgerEntryMapper (ACL)"]
    end
    TEL --> MAP --> LPS --> REPO
    RC --> RS --> FR
    RS --> RCP -->|comando| MQ{{RabbitMQ}}
    MQ --> GRL --> RGS
    SRD --> RCP
    RGS --> TSP
    RGS --> BC
    RGS --> REP
    LTS -.->|"implementa"| TSP
    XLS -.->|"implementa"| REP
    LTS --> REPO
```

As **portas** (`TransactionSource`, `ReportExporter`) são interfaces do domínio; as implementações
ficam na infraestrutura. Assim o cálculo do relatório não depende de JPA nem do Apache POI
(**inversão de dependência**, o "D" do SOLID) e trocar o Excel por PDF significa criar outro
adaptador, sem mexer no domínio (**aberto/fechado**, o "O").

---

## 4. Topologia de mensagens (RabbitMQ)

```mermaid
flowchart LR
    FIN["finora<br/>(OutboxRelay)"] -->|"transaction.registered<br/>transaction.removed"| X1{{"finora.transactions<br/>(topic)"}}
    X1 -->|"binding transaction.#35;"| Q1[["reports.transaction-events"]]
    Q1 --> L1["TransactionEventsListener<br/>(1 consumidor: preserva a ordem)"]

    RS["reports-service<br/>(ReportCommandPublisher)"] -->|"report.generate"| X2{{"finora.reports.commands<br/>(direct)"}}
    X2 --> Q2[["reports.generate-report"]]
    Q2 --> L2["GenerateReportCommandListener<br/>(2 a 4 consumidores concorrentes)"]

    Q1 -.->|"falhou após 3 tentativas"| DLX{{"finora.dlx"}}
    Q2 -.->|"falhou após 3 tentativas"| DLX
    DLX --> D1[["reports.transaction-events.dlq"]]
    DLX --> D2[["reports.generate-report.dlq"]]
```

| Mensagem | Tipo | Significado |
|---|---|---|
| `TransactionRegistered` / `TransactionRemoved` | **Evento** (fato passado, vários consumidores possíveis) | "Uma transação foi registrada/removida" |
| `GenerateReportCommand` | **Comando** (pedido a um único destinatário) | "Gere o relatório X" |

---

## 5. Diagramas de sequência

### 5.1 Login

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuário
    participant FE as Front (Next.js)
    participant GW as gateway-service
    participant F as finora
    participant DB as PostgreSQL finora

    U->>FE: e-mail e senha
    FE->>GW: POST /auth/login
    GW->>F: POST /auth/login (lb://finora)
    F->>DB: busca credencial pelo e-mail
    DB-->>F: hash BCrypt
    F->>F: confere a senha e gera JWT (id, e-mail)
    F-->>GW: 200 { accessToken }
    GW-->>FE: 200 { accessToken }
    FE->>FE: grava o token em cookie httpOnly
    FE-->>U: redireciona para o painel
```

### 5.2 Registrar uma transação (evento + Transactional Outbox)

O ponto central: **salvar a transação e registrar o evento acontecem na mesma transação do banco**.
A publicação no RabbitMQ acontece depois, pelo relay. Assim nenhum evento se perde, mesmo com o
broker fora do ar.

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuário
    participant FE as Front (Next.js)
    participant GW as gateway-service
    participant F as finora
    participant DB as PostgreSQL finora
    participant R as OutboxRelay (finora)
    participant MQ as RabbitMQ
    participant RS as reports-service
    participant DR as PostgreSQL finora_reports

    U->>FE: preenche receita/despesa
    FE->>GW: POST /transactions (Bearer JWT)
    GW->>F: POST /transactions
    F->>F: Transaction.register(...) valida invariantes
    rect rgba(120,160,255,0.15)
        Note over F,DB: uma única transação de banco (@Transactional)
        F->>DB: INSERT transactions
        F->>DB: INSERT outbox_events (TransactionRegistered + traceparent)
    end
    F-->>GW: 201 Created
    GW-->>FE: 201 Created
    FE-->>U: lista atualizada

    loop a cada 1 s
        R->>DB: busca eventos com published_at nulo
        R->>MQ: publica em finora.transactions (transaction.registered)
        MQ-->>R: confirmação (publisher confirm)
        R->>DB: marca published_at
    end

    MQ->>RS: entrega em reports.transaction-events
    RS->>DR: evento já processado? (processed_events)
    alt evento novo
        RS->>DR: upsert ledger_entries + INSERT processed_events
    else duplicado (entrega pelo menos uma vez)
        RS->>RS: descarta (idempotência)
    end
    RS-->>MQ: ack
```

### 5.3 Remover uma transação (lápide)

```mermaid
sequenceDiagram
    autonumber
    participant FE as Front (Next.js)
    participant F as finora
    participant DB as PostgreSQL finora
    participant MQ as RabbitMQ
    participant RS as reports-service
    participant DR as PostgreSQL finora_reports

    FE->>F: DELETE /transactions/{id} (via gateway)
    F->>F: transaction.remove(dono) verifica se pertence ao usuário
    rect rgba(120,160,255,0.15)
        F->>DB: DELETE transactions
        F->>DB: INSERT outbox_events (TransactionRemoved)
    end
    F-->>FE: 204 No Content
    Note over F,MQ: o relay publica transaction.removed
    MQ->>RS: TransactionRemoved
    RS->>DR: marca ledger_entries.removed = true (lápide)
    Note over RS,DR: a lápide impede que um TransactionRegistered<br/>atrasado "ressuscite" a transação
```

### 5.4 Gerar relatório (request-reply assíncrono)

O relatório **não é gerado dentro da requisição HTTP**: o serviço responde `202 Accepted` na hora e
um worker gera o arquivo. O front acompanha o status por polling.

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuário
    participant FE as Front (Next.js)
    participant GW as gateway-service
    participant RS as reports-service (API)
    participant DR as PostgreSQL finora_reports
    participant MQ as RabbitMQ
    participant W as Worker (GenerateReportCommandListener)

    U->>FE: clica em "Extrair relatório"
    FE->>GW: POST /reports
    GW->>RS: POST /reports
    RS->>DR: INSERT reports (status REQUESTED)
    RS-->>FE: 202 Accepted { id, status: REQUESTED }
    Note over RS,MQ: após o commit (ReportCommandPublisher)
    RS->>MQ: GenerateReportCommand (report.generate)

    par worker gera o relatório
        MQ->>W: entrega em reports.generate-report
        W->>DR: lê ledger_entries do dono (TransactionSource)
        W->>W: BalanceCalculator: receitas − despesas = saldo
        W->>W: ExcelReportExporter gera o .xlsx
        W->>DR: INSERT report_contents + reports.status = READY
        W-->>MQ: ack
    and front acompanha (polling a cada 1 s, até 30 s)
        loop enquanto status = REQUESTED
            FE->>GW: GET /reports/{id}
            GW->>RS: GET /reports/{id}
            RS-->>FE: { status }
        end
    end

    FE->>GW: GET /reports/{id}/file
    GW->>RS: GET /reports/{id}/file
    RS-->>FE: 200 arquivo .xlsx
    FE-->>U: download do Excel

    opt falha na geração
        W-->>MQ: rejeita após 3 tentativas
        MQ->>W: mensagem vai para reports.generate-report.dlq
        W->>DR: listener da DLQ marca o relatório FAILED com o motivo
    end
    opt comando perdido ou worker parado
        Note over RS: StaleReportDispatcher (a cada 30 s) reenvia pedidos<br/>parados em REQUESTED e marca FAILED após 10 min
    end
```

### 5.5 Rastreamento distribuído de uma transação

Como o `traceparent` viaja no HTTP, é guardado na outbox e segue no cabeçalho da mensagem, o
Tempo mostra a operação da seção 5.2 como **um único trace**:

```mermaid
sequenceDiagram
    participant GW as gateway-service
    participant F as finora
    participant R as OutboxRelay
    participant RS as reports-service
    Note over GW,RS: traceId único do início ao fim
    GW->>F: span "POST /transactions" (traceparent no HTTP)
    F->>F: span "INSERT transactions + outbox_events"
    F-->>R: traceparent salvo em outbox_events.trace_headers
    R->>RS: span "finora.transactions send" (traceparent no cabeçalho AMQP)
    RS->>RS: span "reports.transaction-events receive"
```

Detalhes em [monitoramento.md](monitoramento.md).

---

## 6. Modelo de dados

Cada serviço tem o próprio banco. Os dados foram modelados a partir das **consultas** que o sistema
faz (índices por dono e por status) e do **isolamento** entre contextos (o `reports-service` guarda
uma cópia mínima dos lançamentos, a projeção, em vez de consultar o banco do `finora`).

### Banco `finora` (contextos de Identidade e Transações)

```mermaid
erDiagram
    USERS ||--|| CREDENTIALS : "possui"
    USERS ||--o{ TRANSACTIONS : "registra"
    TRANSACTIONS ||--o{ OUTBOX_EVENTS : "gera eventos (aggregate_id)"

    USERS {
        uuid id PK
        string name
    }
    CREDENTIALS {
        uuid id PK
        string email
        string hash_password "BCrypt"
        string role "USER"
        boolean enabled
        uuid user_id FK
    }
    TRANSACTIONS {
        uuid id PK "gerado pelo domínio"
        string description
        decimal amount "Money (VO)"
        string type "INCOME ou EXPENSE"
        string category
        date date
        timestamp created_at
        uuid user_id FK
    }
    OUTBOX_EVENTS {
        uuid id PK "id do evento"
        string aggregate_type
        uuid aggregate_id "idx_outbox_aggregate"
        string event_type
        string exchange
        string routing_key
        text payload "JSON"
        timestamp occurred_at
        timestamp published_at "nulo = pendente (idx_outbox_pending)"
        int attempts
        string last_error
        string trace_headers "traceparent"
    }
```

### Banco `finora_reports` (contexto de Relatórios)

```mermaid
erDiagram
    REPORTS ||--o| REPORT_CONTENTS : "arquivo gerado"

    LEDGER_ENTRIES {
        uuid transaction_id PK
        uuid owner_id
        string owner_email "idx_ledger_owner"
        string description
        string category
        string type
        decimal amount
        date entry_date
        boolean removed "lápide"
        timestamp last_event_at
    }
    PROCESSED_EVENTS {
        uuid event_id PK "idempotência"
        string event_type
        timestamp processed_at
    }
    REPORTS {
        uuid id PK
        bigint version "lock otimista"
        string owner_email "idx_reports_owner"
        date period_start "ReportPeriod (VO)"
        date period_end
        decimal total_income "ReportSummary (VO)"
        decimal total_expense
        decimal balance
        int entry_count
        string format
        string status "REQUESTED, READY ou FAILED"
        string file_name
        string failure_reason
        timestamp requested_at
        timestamp completed_at
    }
    REPORT_CONTENTS {
        uuid report_id PK
        string content_type
        bytea content "arquivo .xlsx"
    }
```

| Garantia | Como |
|---|---|
| Integridade das regras | Invariantes nos agregados (`Money` positivo, só o dono remove a transação, transições de status do relatório) |
| Atomicidade | `@Transactional` nos serviços de aplicação; transação e evento gravados juntos (outbox) |
| Concorrência | Lock otimista (`@Version`) em `FinancialReport` |
| Sem processamento duplicado | `processed_events` (chave = id do evento) |
| Desempenho | Índices criados a partir das consultas: `idx_reports_owner`, `idx_reports_status`, `idx_ledger_owner`, `idx_outbox_pending`, `idx_outbox_aggregate` |

---

## 7. Histórico de mudanças dos dados

O sistema guarda o histórico em três lugares, cada um com um propósito:

| Onde | O que registra | Como consultar |
|---|---|---|
| `outbox_events` (finora) | **Todo** registro e remoção de transação, em ordem, com data do fato (`occurred_at`) e da publicação (`published_at`) — um *log* de eventos de domínio | Banco (`SELECT … ORDER BY occurred_at`) e Grafana (métricas `finora_outbox_*`) |
| `ledger_entries` (reports) | Estado mais recente de cada transação, **sem apagar as removidas** (lápide `removed = true`) e com `last_event_at` | Usado pela geração dos relatórios |
| `reports` (reports) | Todos os relatórios pedidos, com período, totais, status e datas | **`GET /reports/history`** e tela de histórico no front |

Exemplo de consulta do histórico de alterações de uma transação:

```sql
SELECT event_type, occurred_at, published_at, payload
FROM outbox_events
WHERE aggregate_id = '<id-da-transação>'
ORDER BY occurred_at;
```

---

## 8. Arquitetura orientada a eventos: prós e contras

Na arquitetura orientada a eventos (EDA), um serviço anuncia o que aconteceu ("transação
registrada") e segue em frente; quem se interessa reage quando puder. No Finora isso substituiu a
chamada síncrona `reports-service → finora` (OpenFeign) por mensagens no RabbitMQ.

### Prós

| Benefício | O que significa | No Finora |
|---|---|---|
| Baixo acoplamento | O produtor não conhece os consumidores | O finora publica eventos sem saber que o reports-service existe; um novo consumidor (ex.: notificações) entra sem mudar o finora |
| Resiliência | Uma falha não se propaga em cascata | Com o finora fora do ar, relatórios continuam sendo gerados a partir da projeção local; com o RabbitMQ fora, o finora continua aceitando transações (a outbox guarda os eventos) |
| Escalabilidade | Trabalho pesado vai para filas e é dividido entre workers | A geração do Excel sai da requisição HTTP e vai para uma fila com consumidores concorrentes; o HPA aumenta as réplicas |
| Absorção de picos | A fila acumula e os consumidores drenam no ritmo deles | Muitos pedidos de relatório ao mesmo tempo não derrubam o serviço |
| Autonomia de dados | Cada serviço mantém a própria cópia do que precisa | O reports-service guarda uma projeção dos lançamentos na base `finora_reports` |
| Auditoria | Eventos formam um histórico do que aconteceu | A tabela `outbox_events` registra todo evento emitido e quando foi publicado |

### Contras

| Custo | O que significa | Como tratamos |
|---|---|---|
| Consistência eventual | O consumidor fica alguns instantes atrás do produtor | Um lançamento criado agora aparece no relatório após ~1 a 2 s (intervalo do relay + consumo) |
| Mais infraestrutura | Um broker a mais para operar e monitorar | RabbitMQ em StatefulSet com volume, painel de gestão e métricas no Grafana |
| Entrega "pelo menos uma vez" | A mesma mensagem pode chegar duas vezes | Consumidor idempotente (`processed_events` + upsert por id) |
| Ordem e falhas parciais | Mensagens podem chegar fora de ordem ou falhar | Fila de eventos com um consumidor, lápides para exclusões, retry com backoff e DLQ |
| Depuração mais difícil | O fluxo não é uma pilha de chamadas | Rastreamento distribuído (Tempo), `traceId` nos logs (Loki), `messageId = eventId` |
| Dupla escrita (banco + broker) | Salvar e publicar não são atômicos | Transactional Outbox |

### Quando vale a pena

- **Vale:** vários serviços reagem ao mesmo fato; tarefas demoradas que não precisam de resposta
  imediata (relatórios, e-mails, exportações); picos de carga; integração entre bounded contexts
  que devem evoluir de forma independente.
- **Não vale:** o usuário precisa da resposta na hora e com consistência forte (ex.: login, validar
  saldo antes de um débito); sistemas pequenos com um único serviço, onde o broker só adiciona custo.
- **No Finora:** login e cadastro de transações continuam **síncronos** (o usuário precisa da
  confirmação). O que passou a usar eventos foi a **integração entre contextos** e a **geração de
  relatórios**.

---

## 9. Diagrama de implantação (Kubernetes)

```mermaid
flowchart TB
    subgraph host["Máquina (Docker Desktop)"]
        subgraph k8s["Cluster Kubernetes — namespace finora"]
            subgraph lb["Services LoadBalancer (localhost)"]
                sfe["frontend :3000"]
                sgw["gateway-service :8080"]
                sgr["grafana :3001"]
                seu["server-service :8761"]
                smq["rabbitmq-management :15672"]
                spr["prometheus :9090"]
            end
            subgraph dep["Deployments (sem estado)"]
                pfe["frontend ×2"]
                pgw["gateway-service ×2"]
                pfi["finora ×2..5 (HPA)"]
                prs["reports-service ×2..5 (HPA)"]
                pse["server-service ×1"]
                pobs["loki · tempo · prometheus · grafana"]
            end
            subgraph sts["StatefulSets (com volume persistente)"]
                ppf[("postgres-finora-0")]
                ppr[("postgres-reports-0")]
                pmq[("rabbitmq-0")]
            end
            cfg["ConfigMap finora-config<br/>Secret finora-secrets"]
        end
    end
    sfe --> pfe
    sgw --> pgw
    pgw --> pfi & prs
    pfi --> ppf
    prs --> ppr
    pfi & prs --> pmq
    cfg -.->|"variáveis de ambiente"| pfi & prs & pgw
```

Detalhes (probes, HPA, PDB, rolling update) em [implantacao.md](implantacao.md); pipeline que
constrói e implanta as imagens em [ci-cd.md](ci-cd.md).

---

## 10. Evolução da arquitetura

| Versão | Entrega | Arquitetura |
|---|---|---|
| `v1.0.0` | TP1 | **Monólito** Spring Boot em camadas (controller → service → repository) com DDD no módulo de transações |
| `v2.0.0` | TP2 | **Microsserviços**: Eureka, API Gateway e `reports-service` com banco próprio, integrado ao finora por HTTP (OpenFeign) |
| `v3.0.0` | TP3/TP4 | **Orientada a eventos**: OpenFeign substituído por RabbitMQ (outbox, projeção, relatório assíncrono, DLQ, idempotência) |
| `v5.0.0` | TP5 | **Pronta para produção**: Docker, Kubernetes, observabilidade, testes abrangentes e CI/CD |
