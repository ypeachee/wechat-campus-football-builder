const DEVELOP_API_BASE_URL = "http://127.0.0.1:8080";
const TRIAL_API_BASE_URL = "https://staging-api.your-domain.example";
const RELEASE_API_BASE_URL = "https://api.your-domain.example";

export function resolveApiBaseUrl(envVersion: string): string {
  if (envVersion === "develop") {
    return DEVELOP_API_BASE_URL;
  }
  if (envVersion === "trial") {
    return TRIAL_API_BASE_URL;
  }
  return RELEASE_API_BASE_URL;
}

export function getApiBaseUrl(): string {
  const info = wx.getAccountInfoSync();
  return resolveApiBaseUrl(info.miniProgram?.envVersion ?? "release");
}
