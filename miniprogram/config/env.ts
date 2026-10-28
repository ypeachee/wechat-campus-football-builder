const LOCAL_API_BASE_URL = "http://127.0.0.1:8080";
const PRODUCTION_API_BASE_URL = "https://api.cugbsoccer.xyz";

// Set to true only when you explicitly want the developer tool to call the local backend.
const USE_LOCAL_API_IN_DEVELOPER_TOOLS = false;

export function resolveApiBaseUrl(envVersion: string): string {
  if (envVersion === "develop") {
    return USE_LOCAL_API_IN_DEVELOPER_TOOLS ? LOCAL_API_BASE_URL : PRODUCTION_API_BASE_URL;
  }
  return PRODUCTION_API_BASE_URL;
}

export function getApiBaseUrl(): string {
  const info = wx.getAccountInfoSync();
  return resolveApiBaseUrl(info.miniProgram?.envVersion ?? "release");
}