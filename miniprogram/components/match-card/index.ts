import type { FixtureCard } from "../../types/domain";
import { fixtureStatusLabel, fixtureStatusTone } from "../../utils/status";

Component({
  properties: {
    match: { type: Object, value: null as FixtureCard | null }
  },
  data: {
    statusLabel: "",
    statusTone: "default"
  },
  observers: {
    match(value: FixtureCard | null) {
      if (!value) {
        return;
      }
      this.setData({
        statusLabel: fixtureStatusLabel(value.status),
        statusTone: fixtureStatusTone(value.status)
      });
    }
  },
  methods: {
    handleTap() {
      const match = this.properties.match as FixtureCard | null;
      if (match) {
        this.triggerEvent("select", { fixtureId: match.id });
      }
    }
  }
});
