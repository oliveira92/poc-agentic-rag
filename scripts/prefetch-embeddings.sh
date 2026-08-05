#!/usr/bin/env bash
#
# Baixa o modelo de embedding ANTES do primeiro boot — resumível, verificado e idempotente.
#
# Por que existe: o Spring AI baixa o ONNX no boot, para ${java.io.tmpdir}. Esse diretório é
# purgado pelo macOS, some no reboot e não existe em contêiner — então "baixa uma vez" era, na
# prática, baixar a cada boot frio. Medido neste link (~200 KB/s), 465 MB = ~39 minutos de
# aplicação parada, sem nenhuma barra de progresso e sem retomar se a rede oscilar.
#
# Aqui o download é um passo explícito: mostra progresso, RETOMA de onde parou (curl -C -),
# verifica SHA-256 e não faz nada se o arquivo já estiver íntegro. Rodar duas vezes é seguro.
#
#   ./scripts/prefetch-embeddings.sh            # baixa o que faltar e verifica
#   ./scripts/prefetch-embeddings.sh --verify   # só confere, não baixa (use no CI)
#   ./scripts/prefetch-embeddings.sh --force    # reobtém mesmo se já existir
#
# Sobrescreva a origem com EMBEDDING_MODEL_URL / EMBEDDING_TOKENIZER_URL para usar um mirror
# interno — o formato é um arquivo por HTTP, não há nada específico do HuggingFace.
set -euo pipefail

CACHE_DIR="${EMBEDDING_CACHE_DIR:-$HOME/.cache/agentic-rag/onnx}"
HF_BASE="https://huggingface.co/Xenova/paraphrase-multilingual-MiniLM-L12-v2/resolve/main"
MODEL_URL="${EMBEDDING_MODEL_URL:-$HF_BASE/onnx/model_quantized.onnx}"
TOKENIZER_URL="${EMBEDDING_TOKENIZER_URL:-$HF_BASE/tokenizer.json}"

# SHA-256 dos artefatos que esta versão do projeto espera. Não é paranoia de supply chain: é o
# que distingue "arquivo truncado por queda de rede" de "arquivo bom" sem carregar o ONNX inteiro
# no runtime nativo — que falharia com uma exceção que não diz nada sobre a causa.
MODEL_SHA256="66fc00f5f29afcaff34092e1bdd20008ca3918265a82fb9695a551e510cc4ebc"
TOKENIZER_SHA256="b60b6b43406a48bf3638526314f3d232d97058bc93472ff2de930d43686fa441"
VERIFY_SHA="${EMBEDDING_VERIFY_SHA:-1}"   # 0 desliga (ex.: mirror interno reempacotado)

MODEL_MIN_BYTES=100000000     # int8 ~112 MB
TOKENIZER_MIN_BYTES=10000000  # ~17 MB

MODE="fetch"
[[ "${1:-}" == "--verify" ]] && MODE="verify"
[[ "${1:-}" == "--force"  ]] && MODE="force"

sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | awk '{print $1}'
  else shasum -a 256 "$1" | awk '{print $1}'; fi
}

human() { awk -v b="$1" 'BEGIN{printf "%.0f MB", b/1048576}'; }

# Um arquivo é bom quando existe e passa do piso de tamanho. O piso pega o caso real e chato —
# download interrompido —, que de outra forma só apareceria como ONNX inválido no boot.
check_file() {
  local path="$1" min="$2" want_sha="$3" name="$4"
  [[ -f "$path" ]] || { echo "   ✗ $name: ausente"; return 1; }
  local size; size=$(wc -c < "$path" | tr -d ' ')
  if (( size < min )); then
    echo "   ✗ $name: $(human "$size") — truncado (mínimo $(human "$min"))"
    return 1
  fi
  if [[ "$VERIFY_SHA" == "1" ]]; then
    local got; got=$(sha256_of "$path")
    if [[ "$got" != "$want_sha" ]]; then
      echo "   ✗ $name: SHA-256 não confere"
      echo "       esperado: $want_sha"
      echo "       obtido:   $got"
      return 1
    fi
  fi
  echo "   ✓ $name: $(human "$size")"
  return 0
}

fetch() {
  local url="$1" path="$2" name="$3"
  echo "→ baixando $name de $(echo "$url" | awk -F/ '{print $3}') (retoma se cair)…"
  # -C - retoma; --retry sobrevive a oscilação; -# dá barra de progresso em vez de silêncio.
  curl -fL -C - --retry 5 --retry-delay 3 --retry-all-errors -# -o "$path" "$url"
}

echo "Cache de embeddings: $CACHE_DIR"
mkdir -p "$CACHE_DIR"

MODEL_PATH="$CACHE_DIR/model_quantized.onnx"
TOKENIZER_PATH="$CACHE_DIR/tokenizer.json"

if [[ "$MODE" == "force" ]]; then
  rm -f "$MODEL_PATH" "$TOKENIZER_PATH"
fi

echo "Conferindo o que já existe:"
model_ok=0;     check_file "$MODEL_PATH"     "$MODEL_MIN_BYTES"     "$MODEL_SHA256"     "modelo (int8)" || model_ok=1
tokenizer_ok=0; check_file "$TOKENIZER_PATH" "$TOKENIZER_MIN_BYTES" "$TOKENIZER_SHA256" "tokenizer"     || tokenizer_ok=1

if [[ "$MODE" == "verify" ]]; then
  if (( model_ok == 0 && tokenizer_ok == 0 )); then
    echo "✓ cache quente — o boot não vai tocar na rede."
    exit 0
  fi
  echo "✗ cache frio ou incompleto. Rode: ./scripts/prefetch-embeddings.sh"
  exit 1
fi

(( model_ok != 0 ))     && fetch "$MODEL_URL"     "$MODEL_PATH"     "modelo (int8, ~112 MB)"
(( tokenizer_ok != 0 )) && fetch "$TOKENIZER_URL" "$TOKENIZER_PATH" "tokenizer (~17 MB)"

echo "Verificando:"
check_file "$MODEL_PATH"     "$MODEL_MIN_BYTES"     "$MODEL_SHA256"     "modelo (int8)"
check_file "$TOKENIZER_PATH" "$TOKENIZER_MIN_BYTES" "$TOKENIZER_SHA256" "tokenizer"

echo
echo "✓ pronto. A aplicação agora sobe sem tocar na rede para embeddings."
echo "  Para exigir isso (CI/contêiner): EMBEDDING_REQUIRE_LOCAL=true"
