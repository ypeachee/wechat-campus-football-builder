import { describe, expect, it } from "vitest";
import { eventTypeLabel, fixtureStatusLabel, roleLabel } from "../utils/status";

describe("status labels", () => {
  it("labels fixture statuses", () => {
    expect(fixtureStatusLabel("DRAFT")).toBe("草稿");
    expect(fixtureStatusLabel("SCHEDULED")).toBe("未开始");
    expect(fixtureStatusLabel("LIVE")).toBe("进行中");
    expect(fixtureStatusLabel("FINISHED")).toBe("已完赛");
    expect(fixtureStatusLabel("CANCELLED")).toBe("已取消");
  });

  it("labels event types", () => {
    expect(eventTypeLabel("GOAL")).toBe("进球");
    expect(eventTypeLabel("OWN_GOAL")).toBe("乌龙球");
    expect(eventTypeLabel("YELLOW_CARD")).toBe("黄牌");
    expect(eventTypeLabel("RED_CARD")).toBe("红牌");
  });

  it("labels roles and keeps unknown values", () => {
    expect(roleLabel("ADMIN")).toBe("联赛管理员");
    expect(roleLabel("TEAM_MANAGER")).toBe("球队管理员");
    expect(roleLabel("VIEWER")).toBe("普通用户");
    expect(fixtureStatusLabel("UNKNOWN" as never)).toBe("UNKNOWN");
  });
});
