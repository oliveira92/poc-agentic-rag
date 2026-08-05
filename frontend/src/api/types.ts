/** Tipos espelhando os records Java da API (fonte: com.example.agenticrag.*). */

export interface Citation {
  index: number;
  source: 'PORTAL_API' | 'README' | 'LLM_APPROVED' | string;
  ref: string;
  score: number | null;
  snippet: string;
}

/** O que os guardrails do M04 fizeram na chamada. */
export type GuardAction = 'ALLOW' | 'MASK' | 'BLOCK';

export interface SecurityVerdict {
  inputAction: GuardAction;
  outputAction: GuardAction;
  /** ids dos controles que dispararam (SEC-xx). */
  controls: string[];
  categories: string[];
}

export interface AdviceResult {
  componentId: string;
  conversationId: string;
  answer: string;
  citations: Citation[];
  grounded: boolean;
  /** null quando a LLM devolveu resposta vazia (não vira rascunho HITL). */
  approvalId: string | null;
  traceId: string | null;
  route: string;
  model: string;
  lowConfidence: boolean;
  unsupportedEndpoints: string[];
  security: SecurityVerdict;
}

export interface AdviseRequest {
  question: string;
  conversationId?: string;
  /** Id de GET /api/v1/models; ausente = roteamento por risco decide. */
  model?: string;
}

export interface IngestionResult {
  componentId: string;
  endpointsIndexed: number;
  readmeChunks: number;
  totalDocuments: number;
  skipped: boolean;
}

export interface ApprovalRecord {
  id: string;
  componentId: string;
  question: string;
  answer: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  reviewer: string | null;
  reviewNote: string | null;
  createdAt: string;
  reviewedAt: string | null;
  vectorDocId: string | null;
}

export interface ModelInfo {
  id: string;
  label: string;
  tier: 'forte' | 'rapido' | 'default' | null;
  description: string | null;
  /** catalog = allow-list versionada; litellm/anthropic = descoberto ao vivo no backend. */
  source: 'catalog' | 'litellm' | 'anthropic';
  /** vendor por trás do alias, quando o gateway informa (anthropic, openai, gemini…). */
  provider: string | null;
}

export type RiskTier = 'LOW' | 'MEDIUM' | 'HIGH';

export interface GateCaseEvaluation {
  id: string;
  risk: RiskTier;
  retrievalHit: boolean;
  groundedness: { score: number; reason: string; judge: string } | null;
  threshold: number;
  passed: boolean;
}

export interface GateReport {
  total: number;
  passed: number;
  hitRate: number;
  avgGroundedness: number;
  gatePassed: boolean;
  cases: GateCaseEvaluation[];
}

export interface FeedbackRequest {
  value: 0 | 1;
  route?: string;
  traceId?: string;
  comment?: string;
}

export interface FeedbackResponse {
  componentId: string;
  route: string;
  recorded: boolean;
}

// ---------- M04 · Segurança ----------

export interface SecurityControl {
  id: string;
  category: string;
  description: string;
  /** classe Java que implementa — o ponteiro para quem for revisar. */
  implementation: string;
  /** ação por estágio; só aparece o estágio em que a categoria é tratada. */
  actions: Partial<Record<'INGESTION' | 'INPUT' | 'OUTPUT', GuardAction>>;
}

export interface SecurityStatus {
  enabled: boolean;
  failMode: 'closed' | 'open';
  scopeJudge: string;
  maxQuestionChars: number;
  maxDocumentChars: number;
  controls: number;
}

export interface SecurityControlsResponse {
  status: SecurityStatus;
  controls: SecurityControl[];
}

/** Toda taxa vem com o seu denominador — ver SecurityEvaluationReport.Metrics. */
export interface SecurityMetrics {
  totalCases: number;
  badCases: number;
  legitimateCases: number;
  badBlocked: number;
  badAllowed: number;
  legitimateBlocked: number;
  legitimateAllowed: number;
  legitimateMasked: number;
}

export interface SecurityCaseResult {
  id: string;
  type: 'legitimo' | 'abuso' | 'fora_escopo' | string;
  question: string;
  shouldBlock: boolean;
  baselineAction: GuardAction;
  action: GuardAction;
  controls: string[];
  categories: string[];
  outcome: 'TP' | 'FP' | 'TN' | 'FN';
  note: string;
}

export interface SecurityEvaluationReport {
  /** id do cenário: 'componentes' | 'a05-seguros'. */
  scenarioId: string;
  scenario: string;
  judge: string;
  baseline: SecurityMetrics;
  protectedRun: SecurityMetrics;
  cases: SecurityCaseResult[];
}

export interface SecurityScenario {
  name: string;
  label: string;
  cases: number;
  isDefault: boolean;
}

/** RFC 7807 — como o ApiExceptionHandler responde erros. */
export interface ProblemDetail {
  status?: number;
  title?: string;
  detail?: string;
  /** presentes no 422 de guardrail. */
  stage?: string;
  controls?: string[];
}
