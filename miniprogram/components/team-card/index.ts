import type { TeamSummary } from "../../types/domain";

Component({
  properties: {
    team: { type: Object, value: null as TeamSummary | null }
  },
  data: {
    badgeText: ""
  },
  observers: {
    team(value: TeamSummary | null) {
      if (!value) {
        return;
      }
      this.setData({
        badgeText: (value.shortName || value.name).slice(0, 2)
      });
    }
  },
  methods: {
    handleTap() {
      const team = this.properties.team as TeamSummary | null;
      if (team) {
        this.triggerEvent("select", { teamId: team.id });
      }
    }
  }
});
