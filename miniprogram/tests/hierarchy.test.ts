import { describe, expect, it } from "vitest";
import { withAllGroups } from "../services/competition-hierarchy";

describe("competition hierarchy", () => {
  it("prepends an all-groups option without mutating the API result", () => {
    const groups = [{ id: 11, stageId: 7, name: "A组", sortOrder: 1 }];
    const options = withAllGroups(7, groups);

    expect(options[0]).toEqual({ id: 0, stageId: 7, name: "全部小组", sortOrder: -1 });
    expect(options[1]).toEqual(groups[0]);
    expect(groups).toHaveLength(1);
  });
});
