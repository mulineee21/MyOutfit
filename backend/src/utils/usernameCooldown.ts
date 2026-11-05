export const USERNAME_COOLDOWN_DAYS = 30;
const DAY_MS = 24 * 60 * 60 * 1000;
const COOLDOWN_MS = USERNAME_COOLDOWN_DAYS * DAY_MS;

export interface UsernameCooldownInfo {
  canChangeUsername: boolean;
  nextUsernameChangeAt: string | null;
  usernameCooldownDaysRemaining: number;
}

export function computeUsernameCooldown(
  usernameChangedAt: Date | null | undefined,
  nowMs: number = Date.now(),
): UsernameCooldownInfo {
  if (!usernameChangedAt) {
    return {
      canChangeUsername: true,
      nextUsernameChangeAt: null,
      usernameCooldownDaysRemaining: 0,
    };
  }

  const nextMs = usernameChangedAt.getTime() + COOLDOWN_MS;
  if (nowMs >= nextMs) {
    return {
      canChangeUsername: true,
      nextUsernameChangeAt: null,
      usernameCooldownDaysRemaining: 0,
    };
  }

  const daysRemaining = Math.max(1, Math.ceil((nextMs - nowMs) / DAY_MS));
  return {
    canChangeUsername: false,
    nextUsernameChangeAt: new Date(nextMs).toISOString(),
    usernameCooldownDaysRemaining: daysRemaining,
  };
}

export function formatSpanishDate(isoOrDate: Date | string): string {
  const d = typeof isoOrDate === 'string' ? new Date(isoOrDate) : isoOrDate;
  const day = String(d.getUTCDate()).padStart(2, '0');
  const month = String(d.getUTCMonth() + 1).padStart(2, '0');
  const year = d.getUTCFullYear();
  return `${day}/${month}/${year}`;
}
