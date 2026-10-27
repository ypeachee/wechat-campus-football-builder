import { describe, expect, it } from "vitest";
import { SubmitLock } from "../state/submit-lock";

describe("SubmitLock", () => {
  it("rejects concurrent submissions until released", () => {
    const lock = new SubmitLock();
    expect(lock.tryAcquire()).toBe(true);
    expect(lock.tryAcquire()).toBe(false);
    lock.release();
    expect(lock.tryAcquire()).toBe(true);
  });
});
