import { describe, expect, it } from "vitest";
import { resolveApiBaseUrl } from "../config/env";

describe("resolveApiBaseUrl", () => {
  it("uses the production API in the developer tool by default", () => {
    expect(resolveApiBaseUrl("develop")).toBe("https://api.cugbsoccer.xyz");
  });

  it("uses the production API for trial and release", () => {
    expect(resolveApiBaseUrl("trial")).toBe("https://api.cugbsoccer.xyz");
    expect(resolveApiBaseUrl("release")).toBe("https://api.cugbsoccer.xyz");
  });
});