import { refreshSession } from "../../services/auth";
import {
  createSensitiveTerm, deleteSensitiveTerm, fetchModerationRatings, fetchSensitiveTerms,
  importSensitiveTerms, moderateRating, updateSensitiveTerm
} from "../../services/ratings";
import type { RatingModerationItem, RatingStatus, SensitiveTerm, SensitiveTermAction } from "../../types/domain";
import { errorMessage } from "../../utils/error";

type Tab = "ratings" | "terms";

Page({
  data: {
    verified: false,
    loading: true,
    busy: false,
    error: "",
    activeTab: "ratings" as Tab,
    filters: [
      { value: "PENDING", label: "待审核" },
      { value: "REPORTED", label: "被举报" },
      { value: "HIDDEN", label: "已隐藏" }
    ],
    selectedFilter: "PENDING",
    items: [] as RatingModerationItem[],
    total: 0,
    terms: [] as SensitiveTerm[],
    termName: "",
    termCategory: "通用",
    termAction: "REVIEW" as SensitiveTermAction,
    importText: "",
    importAction: "REVIEW" as SensitiveTermAction
  },

  onLoad() { void this.verifyAndLoad(); },
  onPullDownRefresh() { void this.verifyAndLoad(); },

  async verifyAndLoad() {
    this.setData({ loading: true, error: "" });
    try {
      const session = await refreshSession();
      if (!session || session.role !== "ADMIN") {
        this.setData({ verified: false, error: "仅联赛管理员可审核评价" });
        return;
      }
      this.setData({ verified: true });
      await this.loadActive();
    } catch (error) {
      this.setData({ error: errorMessage(error) });
    } finally {
      this.setData({ loading: false });
      wx.stopPullDownRefresh();
    }
  },

  async loadActive() {
    if (this.data.activeTab === "ratings") {
      await this.loadRatings();
    } else {
      await this.loadTerms();
    }
  },

  async loadRatings() {
    const page = await fetchModerationRatings(this.data.selectedFilter, 0, 50);
    this.setData({ items: page.items, total: page.total });
  },

  async loadTerms() {
    this.setData({ terms: await fetchSensitiveTerms() });
  },

  switchTab(event: WechatMiniprogram.BaseEvent) {
    const tab = String(event.currentTarget.dataset.tab) as Tab;
    this.setData({ activeTab: tab });
    void this.loadActive().catch((error) => wx.showToast({ title: errorMessage(error), icon: "none" }));
  },

  selectFilter(event: WechatMiniprogram.BaseEvent) {
    this.setData({ selectedFilter: String(event.currentTarget.dataset.value) });
    void this.loadRatings().catch((error) => wx.showToast({ title: errorMessage(error), icon: "none" }));
  },

  moderate(event: WechatMiniprogram.BaseEvent) {
    const ratingId = Number(event.currentTarget.dataset.id);
    const action = String(event.currentTarget.dataset.action);
    const status: RatingStatus = action === "approve" ? "VISIBLE" : action === "reject" ? "REJECTED" : "HIDDEN";
    const title = action === "approve" ? "通过评价" : action === "reject" ? "拒绝评价" : "隐藏评价";
    wx.showModal({
      title,
      editable: true,
      placeholderText: "审核原因，可选",
      success: async (result) => {
        if (!result.confirm) return;
        this.setData({ busy: true });
        try {
          await moderateRating(ratingId, status, result.content ?? "");
          await this.loadRatings();
          wx.showToast({ title: "审核已处理", icon: "success" });
        } catch (error) {
          wx.showToast({ title: errorMessage(error), icon: "none" });
        } finally {
          this.setData({ busy: false });
        }
      }
    });
  },

  input(event: WechatMiniprogram.Input) {
    this.setData({ [String(event.currentTarget.dataset.field)]: event.detail.value });
  },

  selectTermAction(event: WechatMiniprogram.BaseEvent) {
    const field = String(event.currentTarget.dataset.field);
    this.setData({ [field]: String(event.currentTarget.dataset.value) });
  },

  async createTerm() {
    if (!this.data.termName.trim() || !this.data.termCategory.trim()) {
      wx.showToast({ title: "请填写敏感词和分类", icon: "none" });
      return;
    }
    this.setData({ busy: true });
    try {
      await createSensitiveTerm(this.data.termName, this.data.termCategory, this.data.termAction);
      this.setData({ termName: "" });
      await this.loadTerms();
      wx.showToast({ title: "敏感词已添加", icon: "success" });
    } catch (error) {
      wx.showToast({ title: errorMessage(error), icon: "none" });
    } finally {
      this.setData({ busy: false });
    }
  },

  async toggleTerm(event: WechatMiniprogram.BaseEvent) {
    const term = this.data.terms.find((item) => item.id === Number(event.currentTarget.dataset.id));
    if (!term) return;
    try {
      await updateSensitiveTerm(term.id, term.category, term.action, !term.enabled);
      await this.loadTerms();
    } catch (error) {
      wx.showToast({ title: errorMessage(error), icon: "none" });
    }
  },

  async removeTerm(event: WechatMiniprogram.BaseEvent) {
    const id = Number(event.currentTarget.dataset.id);
    wx.showModal({
      title: "删除敏感词",
      content: "确认删除该词条？",
      success: async (result) => {
        if (!result.confirm) return;
        try {
          await deleteSensitiveTerm(id);
          await this.loadTerms();
        } catch (error) {
          wx.showToast({ title: errorMessage(error), icon: "none" });
        }
      }
    });
  },

  async importTerms() {
    const terms = this.data.importText.split(/\r?\n/).map((item) => item.trim()).filter(Boolean);
    if (terms.length === 0) {
      wx.showToast({ title: "请填写批量词条", icon: "none" });
      return;
    }
    this.setData({ busy: true });
    try {
      const result = await importSensitiveTerms(terms, this.data.importAction);
      this.setData({ importText: "" });
      await this.loadTerms();
      wx.showToast({ title: `已导入 ${result.inserted} 条`, icon: "success" });
    } catch (error) {
      wx.showToast({ title: errorMessage(error), icon: "none" });
    } finally {
      this.setData({ busy: false });
    }
  },

  goBack() { wx.navigateBack(); }
});
