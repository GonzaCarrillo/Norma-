#!/usr/bin/env bash
#
# orca-setup.sh — Configuración local de worktree Orca (topología oficial).
#
# Sin copia de AGENTS.override.md (modo contingencia desactivado).
# Mantener únicamente para compatibilidad de entornos Orca.

set -euo pipefail

WT="${ORCA_WORKTREE_PATH:-}"

if [[ -z "$WT" ]]; then
  echo "orca-setup: ORCA_WORKTREE_PATH no definida, se omite configuración de worktree" >&2
  exit 0
fi

echo "orca-setup: worktree $WT listo (topología oficial: Codex / Antigravity / Copilot)"
