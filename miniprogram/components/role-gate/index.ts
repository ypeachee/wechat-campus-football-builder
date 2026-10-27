import { requireRole } from "../../state/session";
import type { UserRole } from "../../types/domain";

Component({
  properties: {
    roles: { type: Array, value: [] as UserRole[] },
    teamId: { type: Number, value: 0 }
  },
  data: {
    allowed: false
  },
  observers: {
    "roles, teamId"(roles: UserRole[], teamId: number) {
      this.setData({
        allowed: requireRole(roles, teamId > 0 ? teamId : undefined)
      });
    }
  },
  lifetimes: {
    attached() {
      const roles = this.properties.roles as UserRole[];
      const teamId = this.properties.teamId as number;
      this.setData({ allowed: requireRole(roles, teamId > 0 ? teamId : undefined) });
    }
  },
  methods: {
    refreshGate() {
      const roles = this.properties.roles as UserRole[];
      const teamId = this.properties.teamId as number;
      this.setData({ allowed: requireRole(roles, teamId > 0 ? teamId : undefined) });
    }
  }
});
