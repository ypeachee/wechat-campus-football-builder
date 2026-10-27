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

  it("excludes unused TDesign components and development files from the package", () => {
    const ignored = new Set(config.packOptions.ignore.map((entry: { value: string }) => entry.value));
    expect([...ignored]).toEqual(expect.arrayContaining([
      "assets",
      "tests",
      "typings",
      "package-lock.json",
      "miniprogram_npm/tdesign-miniprogram/upload",
      "miniprogram_npm/tdesign-miniprogram/chat-markdown"
    ]));
    expect(config.setting.uploadWithSourceMap).toBe(false);
    expect(ignored).not.toContain("miniprogram_npm/tdesign-miniprogram/config-provider");

    const app = JSON.parse(fs.readFileSync(path.resolve(__dirname, "../app.json"), "utf8"));
    expect(app.usingComponents).toBeUndefined();

    const stats = JSON.parse(fs.readFileSync(path.resolve(__dirname, "../pages/stats/index.json"), "utf8"));
    const seasonPicker = JSON.parse(fs.readFileSync(path.resolve(__dirname, "../components/season-picker/index.json"), "utf8"));
    expect(stats.usingComponents).toHaveProperty("t-tabs");
    expect(seasonPicker.usingComponents).toHaveProperty("t-picker");
  });

  it("keeps the projected main package below 1.5 MB", () => {
    const miniprogramRoot = path.resolve(__dirname, "..");
    const rules = config.packOptions.ignore as Array<{ type: string; value: string }>;

    function isIgnored(relativePath: string): boolean {
      const normalized = relativePath.replace(/\\/g, "/");
      return rules.some((rule) => {
        const value = rule.value.replace(/\\/g, "/");
        if (rule.type === "folder") {
          return normalized === value || normalized.startsWith(`${value.replace(/\/$/, "")}/`);
        }
        if (rule.type === "file") {
          return normalized === value;
        }
        return rule.type === "suffix" && normalized.endsWith(value);
      });
    }

    function includedBytes(directory: string): number {
      return fs.readdirSync(directory, { withFileTypes: true }).reduce((total, entry) => {
        const fullPath = path.join(directory, entry.name);
        const relativePath = path.relative(miniprogramRoot, fullPath);
        if (isIgnored(relativePath)) {
          return total;
        }
        return total + (entry.isDirectory() ? includedBytes(fullPath) : fs.statSync(fullPath).size);
      }, 0);
    }

    expect(includedBytes(miniprogramRoot)).toBeLessThanOrEqual(1.5 * 1024 * 1024);
  });

  it("registers every page with all required source files", () => {
    const app = JSON.parse(fs.readFileSync(path.resolve(__dirname, "../app.json"), "utf8"));
    expect(app.pages).toContain("pages/manage/index");
    for (const page of app.pages as string[]) {
      for (const extension of ["ts", "json", "wxml", "wxss"]) {
        expect(fs.existsSync(path.resolve(__dirname, `../${page}.${extension}`))).toBe(true);
      }
    }
  });

  it("keeps runtime image and audio assets below 200 KB", () => {
    const mediaExtensions = new Set([".png", ".jpg", ".jpeg", ".gif", ".webp", ".mp3", ".wav", ".m4a"]);
    const miniprogramRoot = path.resolve(__dirname, "..");

    function totalMediaBytes(directory: string): number {
      return fs.readdirSync(directory, { withFileTypes: true }).reduce((total, entry) => {
        if (entry.name === "node_modules" || entry.name === "miniprogram_npm") {
          return total;
        }
        const fullPath = path.join(directory, entry.name);
        return total + (entry.isDirectory()
          ? totalMediaBytes(fullPath)
          : mediaExtensions.has(path.extname(entry.name).toLowerCase()) ? fs.statSync(fullPath).size : 0);
      }, 0);
    }

    expect(totalMediaBytes(miniprogramRoot)).toBeLessThanOrEqual(200 * 1024);
  });
});
