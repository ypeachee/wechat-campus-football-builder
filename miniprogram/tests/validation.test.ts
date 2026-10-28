import { describe, expect, it, vi } from "vitest";
import { validatePlayerForm, validateTeamForm } from "../utils/validation";
import { SubmitLock, withSubmitLock } from "../state/submit-lock";

describe("management form validation", () => {
  it("requires and trims a team name", () => {
    expect(validateTeamForm({ name: "  " }, true).name).toBe("请输入球队名称");
    expect(validateTeamForm({ name: "  Campus FC  " }, true).name).toBeUndefined();
  });

  it("blocks team leaders from setting inactive", () => {
    expect(validateTeamForm({ name: "Team", status: "INACTIVE" }, false).status)
      .toBe("领队不能修改球队状态");
    expect(validateTeamForm({ name: "Team", status: "INACTIVE" }, true).status)
      .toBeUndefined();
  });

  it("validates player names and jersey numbers", () => {
    expect(validatePlayerForm({ name: "", jerseyNo: "A1" }).name).toBe("请输入球员姓名");
    expect(validatePlayerForm({ name: "Player", jerseyNo: "ABCDEFGHIJKLMNOPQRSTU" }).jerseyNo)
      .toBe("球衣号码不能超过 20 个字符");
    expect(validatePlayerForm({ name: "Player", jerseyNo: "A1" }).jerseyNo)
      .toBeUndefined();
  });

  it("releases the submit lock on success and failure", async () => {
    const successLock = new SubmitLock();
    await withSubmitLock(successLock, async () => "ok");
    expect(successLock.tryAcquire()).toBe(true);
    successLock.release();

    const failureLock = new SubmitLock();
    await expect(withSubmitLock(failureLock, async () => {
      throw new Error("failed");
    })).rejects.toThrow("failed");
    expect(failureLock.tryAcquire()).toBe(true);
  });

  it("does not run a concurrent submission", async () => {
    const lock = new SubmitLock();
    const task = vi.fn(async () => "ok");
    const first = withSubmitLock(lock, task);
    const second = withSubmitLock(lock, task);
    await expect(second).resolves.toBeUndefined();
    await first;
    expect(task).toHaveBeenCalledTimes(1);
  });
});
