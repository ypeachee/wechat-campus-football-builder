import { describe, expect, it, vi } from "vitest";
import { validatePlayerForm, validateTeamForm } from "../utils/validation";
import { SubmitLock, withSubmitLock } from "../state/submit-lock";

describe("management form validation", () => {
  it("requires and trims a team name", () => {
    expect(validateTeamForm({ name: "  " }, true).name).toBe("请输入球队名称");
    expect(validateTeamForm({ name: "  Campus FC  " }, true).name).toBeUndefined();
  });

  it("blocks team managers from setting inactive", () => {
    expect(validateTeamForm({ name: "Team", status: "INACTIVE" }, false).status)
      .toBe("球队管理员不能修改球队状态");
    expect(validateTeamForm({ name: "Team", status: "INACTIVE" }, true).status)
      .toBeUndefined();
  });

  it("validates player names and jersey numbers", () => {
    expect(validatePlayerForm({ name: "", jerseyNo: 9 }).name).toBe("请输入球员姓名");
    expect(validatePlayerForm({ name: "Player", jerseyNo: 1000 }).jerseyNo)
      .toBe("球衣号码必须是 0 到 999 的整数");
    expect(validatePlayerForm({ name: "Player", jerseyNo: 9 }).jerseyNo)
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
