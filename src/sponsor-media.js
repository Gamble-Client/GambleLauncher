const DEFAULT_SITE = "https://gambleclient.org";

export function resolveSponsorMediaUrl(value, site = DEFAULT_SITE) {
  const raw = String(value || "").trim();
  if (!raw) return "";

  try {
    const base = new URL(site);
    const resolved = new URL(raw, base);
    const host = resolved.hostname.toLowerCase();
    const allowedHost = host === "gambleclient.org"
      || host.endsWith(".gambleclient.org")
      || host === "gamble-client.store"
      || host.endsWith(".gamble-client.store");
    if (resolved.protocol !== "https:" || !allowedHost || resolved.username || resolved.password || resolved.port) {
      return "";
    }
    return resolved.href;
  } catch {
    return "";
  }
}
