import { describe, expect, it } from "vitest";
import { resolveApiBaseUrl } from "../config/env";

describe("resolveApiBaseUrl", () => {
  it("uses local API for develop", () => {
    expect(resolveApiBaseUrl("develop")).toBe("http://127.0.0.1:8080");
  });

  it("uses the production API domain for trial and release", () => {
    expect(resolveApiBaseUrl("trial")).toBe("https://api.cugbsoccer.xyz");
    expect(resolveApiBaseUrl("release")).toBe("https://api.cugbsoccer.xyz");
  });
});