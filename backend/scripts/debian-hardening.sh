#!/usr/bin/env bash
# ==============================================================================
# Script de Preparación y Seguridad Base para Servidor Debian 12/13 (Bookworm/Trixie)
# Ejecutar como root o con sudo: sudo bash scripts/debian-hardening.sh
# ==============================================================================
set -euo pipefail

SSH_PORT="${SSH_PORT:-22}"

echo "[1/4] Instalando UFW y Fail2Ban..."
apt-get update -qq
apt-get install -y ufw fail2ban curl ca-certificates gnupg

echo "[2/4] Configurando políticas por defecto del Firewall (UFW)..."
ufw default deny incoming
ufw default allow outgoing

# Limitar intentos de fuerza bruta en SSH (rate-limit)
ufw limit "${SSH_PORT}/tcp" comment 'SSH con proteccion anti-fuerza-bruta'

# Permitir exclusivamente tráfico web hacia Caddy (HTTP, HTTPS y HTTP/3 QUIC)
ufw allow 80/tcp comment 'HTTP - Caddy ACME y redireccion HTTPS'
ufw allow 443/tcp comment 'HTTPS - Caddy API MyOutfit'
ufw allow 443/udp comment 'HTTP/3 QUIC - Caddy API MyOutfit'

echo "[3/4] Activando UFW y Fail2Ban..."
ufw --force enable
systemctl enable --now fail2ban

echo "[4/4] Estado actual del firewall:"
ufw status verbose

echo ""
echo "Listo. Recuerda que PostgreSQL (5432) y Node.js (3000) NO se publican al host;"
echo "permanecen aislados en la red Docker 'backend_net' detrás de Caddy."
