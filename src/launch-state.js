// Presentation only. The native launcher and loader still authorize every launch.
export function launchState({ profile, signedIn, running, starting, busy, buildId, ads, updateAvailable }) {
  if (starting) return { action: "wait", label: "Starting launcher", detail: "Checking your saved accounts and profiles.", disabled: true };
  if (running) return { action: "stop", label: "Stop Minecraft", detail: "Minecraft is running. Your launcher stays here when you finish.", disabled: busy };
  if (!signedIn) return { action: "signin", label: "Sign in to play", detail: "Connect your Gamble account. Minecraft accounts are managed separately.", disabled: busy };
  if (profile.client && buildId === "ad_tier" && !ads?.active) {
    return { action: "dashboard", label: "Open Dashboard", detail: "Watch the 30-second sponsor in your browser, then return and press Play. Access is checked again when you play.", disabled: busy };
  }
  return {
    action: "play",
    label: updateAvailable && profile.client ? "Set up & play" : "Play",
    detail: profile.client ? "Minecraft, Fabric and your client are checked and installed automatically." : "Minecraft files are checked automatically. This profile does not use Gamble Client.",
    disabled: busy
  };
}
