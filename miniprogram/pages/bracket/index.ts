import { fetchBracket } from "../../services/competition";
import type { BracketView } from "../../types/domain";
import { errorMessage } from "../../utils/error";

Page({
  data: {
    loading: true,
    error: "",
    stageId: 0,
    bracket: null as BracketView | null
  },

  onLoad(options: Record<string, string | undefined>) {
    const stageId = Number(options.stageId ?? 0);
    this.setData({ stageId });
    void this.loadBracket(stageId);
  },

  onPullDownRefresh() {
    void this.loadBracket(this.data.stageId);
  },

  async loadBracket(stageId: number) {
    if (!stageId) {
      this.setData({ loading: false, error: "缺少淘汰赛阶段" });
      return;
    }
    this.setData({ loading: true, error: "" });
    try {
      const bracket = await fetchBracket(stageId);
      this.setData({ bracket });
      wx.setNavigationBarTitle({ title: bracket.stageName || "淘汰赛" });
    } catch (error) {
      this.setData({ error: errorMessage(error) });
    } finally {
      this.setData({ loading: false });
      wx.stopPullDownRefresh();
    }
  },

  retry() {
    void this.loadBracket(this.data.stageId);
  }
});
