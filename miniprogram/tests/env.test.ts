import { describe, expect, it } from "vitest";
import { resolveApiBaseUrl } from "../config/env";

describe("resolveApiBaseUrl", () => {
  it("uses local API for develop", () => {
    expect(resolveApiBaseUrl("develop")).toBe("http://127.0.0.1:8080");
  });

  it("uses HTTPS for trial and release", () => {
    expect(resolveApiBaseUrl("trial")).toMatch(/^https:\/\//);
    expect(resolveApiBaseUrl("release")).toMatch(/^https:\/\//);
  });
});
