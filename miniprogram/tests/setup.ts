import { beforeEach, vi } from "vitest";

const storage = new Map<string, unknown>();

beforeEach(() => {
  storage.clear();
  (globalThis as unknown as { wx: unknown }).wx = {
    getStorageSync: vi.fn((key: string) => storage.get(key) ?? ""),
    setStorageSync: vi.fn((key: string, value: unknown) => {
      storage.set(key, value);
    }),
    removeStorageSync: vi.fn((key: string) => {
      storage.delete(key);
    }),
    getAccountInfoSync: vi.fn(() => ({ miniProgram: { envVersion: "develop" } })),
    request: vi.fn()
  };
});
