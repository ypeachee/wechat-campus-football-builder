import { refreshSession } from "../../services/auth";
import { createTieFixture, fetchBracket, generateBracket } from "../../services/bracket";
import type { BracketView } from "../../types/domain";
import { errorMessage } from "../../utils/error";

Page({
  data: { verified: false, loading: true, error: "", knockoutStageId: "", groupStageId: "", qualifiersPerGroup: "2", thirdPlace: true, referee: "", bracket: null as BracketView | null },
  onLoad() { void this.verify(); },
  async verify() {
    this.setData({ loading: true, error: "" });
    try {
      const session = await refreshSession();
      if (!session || session.role !== "ADMIN") { this.setData({ verified: false, error: "仅管理员可管理淘汰赛" }); return; }
      this.setData({ verified: true });
    } catch (error) { this.setData({ error: errorMessage(error) }); }
    finally { this.setData({ loading: false }); }
  },
  input(e: WechatMiniprogram.Input) { this.setData({ [String(e.currentTarget.dataset.field)]: e.detail.value }); },
  switchThirdPlace(e: WechatMiniprogram.SwitchChange) { this.setData({ thirdPlace: e.detail.value }); },
  async generate() {
    const knockoutStageId = Number(this.data.knockoutStageId);
    const groupStageId = Number(this.data.groupStageId);
    const qualifiers = Number(this.data.qualifiersPerGroup);
    if (!knockoutStageId || !groupStageId || (qualifiers !== 1 && qualifiers !== 2)) {
      wx.showToast({ title: "请填写正确的阶段 ID 和每组晋级数", icon: "none" }); return;
    }
    try { const bracket = await generateBracket(knockoutStageId, groupStageId, qualifiers, this.data.thirdPlace); this.setData({ bracket }); wx.showToast({ title: "淘汰赛已生成", icon: "success" }); }
    catch (error) { wx.showToast({ title: errorMessage(error), icon: "none" }); }
  },
  async loadBracket() {
    const stageId = Number(this.data.knockoutStageId);
    if (!stageId) return;
    try { this.setData({ bracket: await fetchBracket(stageId, true) }); }
    catch (error) { wx.showToast({ title: errorMessage(error), icon: "none" }); }
  },
  async createFixture(event: WechatMiniprogram.BaseEvent) {
    const tieId = Number(event.currentTarget.dataset.id);
    if (!tieId) return;
    try { await createTieFixture(tieId, null, null, this.data.referee.trim() || null); wx.showToast({ title: "比赛已创建", icon: "success" }); await this.loadBracket(); }
    catch (error) { wx.showToast({ title: errorMessage(error), icon: "none" }); }
  },
  goBack() { wx.navigateBack(); }
});
