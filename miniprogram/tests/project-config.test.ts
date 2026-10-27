import { describe, expect, it } from "vitest";
import fs from "node:fs";
import path from "node:path";

describe("project.config.json", () => {
  const config = JSON.parse(fs.readFileSync(path.resolve(__dirname, "../project.config.json"), "utf8"));

  it("keeps the real AppID separate from compiler plugins", () => {
    expect(config.compileType).toBe("miniprogram");
    expect(config.appid).toMatch(/^wx/);
    expect(config.setting.useCompilerPlugins).toEqual(["typescript"]);
    expect(config.setting.useCompilerPlugins).not.toContain(config.appid);
  });
});
