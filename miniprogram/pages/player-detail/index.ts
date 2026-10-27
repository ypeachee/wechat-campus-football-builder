import { fetchPlayer } from "../../services/teams";
import type { PlayerSummary } from "../../types/domain";
import { errorMessage } from "../../utils/error";

type PlayerDetailData = {
  loading: boolean;
  error: string;
  playerId: number;
  player: PlayerSummary | null;
};

Page({
  data: {
    loading: true,
    error: "",
    playerId: 0,
    player: null as PlayerSummary | null
  } as PlayerDetailData,

  onLoad(options: Record<string, string | undefined>) {
    const playerId = Number(options.id ?? 0);
    this.setData({ playerId });
    void this.loadPlayer(playerId);
  },

  async loadPlayer(playerId: number) {
    if (!playerId) {
      this.setData({ loading: false, error: "球员不存在" });
      return;
    }
    this.setData({ loading: true, error: "" });
    try {
      const player = await fetchPlayer(playerId);
      this.setData({ player });
      wx.setNavigationBarTitle({ title: player.name });
    } catch (error) {
      this.setData({ error: errorMessage(error) });
    } finally {
      this.setData({ loading: false });
    }
  },

  retry() {
    void this.loadPlayer(this.data.playerId);
  }
});
