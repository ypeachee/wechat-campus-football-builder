import { fetchPlayerRatingSummary } from "../../services/ratings";
import { fetchPlayer } from "../../services/teams";
import type { PlayerSeasonRatingSummary, PlayerSummary } from "../../types/domain";
import { formatRatingScore, starLabel } from "../../utils/rating";
import { errorMessage } from "../../utils/error";

type PlayerDetailData = {
  loading: boolean;
  error: string;
  playerId: number;
  player: PlayerSummary | null;
  ratingSummary: PlayerSeasonRatingSummary | null;
  ratingScoreLabel: string;
  ratingStarsLabel: string;
};

Page({
  data: {
    loading: true,
    error: "",
    playerId: 0,
    player: null as PlayerSummary | null,
    ratingSummary: null as PlayerSeasonRatingSummary | null,
    ratingScoreLabel: "-",
    ratingStarsLabel: "☆☆☆☆☆"
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
      const [player, ratingSummary] = await Promise.all([
        fetchPlayer(playerId),
        fetchPlayerRatingSummary(playerId).catch(() => null)
      ]);
      this.setData({
        player,
        ratingSummary,
        ratingScoreLabel: formatRatingScore(ratingSummary?.averageScore ?? null),
        ratingStarsLabel: starLabel((ratingSummary?.averageScore ?? 0) / 2)
      });
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
