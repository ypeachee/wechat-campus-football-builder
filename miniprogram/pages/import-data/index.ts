import { refreshSession } from "../../services/auth";
import { uploadImportFile, type ImportKind } from "../../services/imports";
import type { ImportPreview, ImportResult, Session } from "../../types/domain";
import { errorMessage } from "../../utils/error";

Page({
  data: {
    loading: true, error: "", session: null as Session | null,
    kindOptions: [
      { value: "players", label: "球员与号码" },
      { value: "teams", label: "球队资料" }
    ] as Array<{ value: ImportKind; label: string }>,
    kindIndex: 0, overwrite: false, fileName: "", filePath: "",
    previewing: false, committing: false, preview: null as ImportPreview | null,
    result: null as ImportResult | null
  },

  onLoad() { void this.load(); },
  onPullDownRefresh() { void this.load(); },

  async load() {
    this.setData({ loading: true, error: "" });
    try {
      const session = await refreshSession();
      if (!session || (session.role !== "ADMIN" && session.role !== "TEAM_MANAGER" && session.role !== "TEAM_LEADER")) {
        this.setData({ session: null, error: "没有批量导入权限" });
        return;
      }
      this.setData({ session });
    } catch (error) { this.setData({ error: errorMessage(error) }); }
    finally { this.setData({ loading: false }); wx.stopPullDownRefresh(); }
  },

  selectKind(event: WechatMiniprogram.PickerChange) {
    this.setData({ kindIndex: Number(event.detail.value), preview: null, result: null });
  },

  toggleOverwrite(event: WechatMiniprogram.SwitchChange) {
    this.setData({ overwrite: event.detail.value, preview: null, result: null });
  },

  chooseFile() {
    wx.chooseMessageFile({
      count: 1,
      type: "file",
      extension: ["csv", "xlsx"],
      success: (result) => {
        const file = result.tempFiles[0];
        if (!file) return;
        this.setData({ fileName: file.name, filePath: file.path, preview: null, result: null });
      }
    });
  },

  async previewFile() {
    if (!this.data.filePath || this.data.previewing) {
      wx.showToast({ title: "请先选择文件", icon: "none" });
      return;
    }
    const kind = this.data.kindOptions[this.data.kindIndex]?.value;
    this.setData({ previewing: true, result: null });
    try {
      const preview = await uploadImportFile(kind, "preview", this.data.filePath, this.data.overwrite) as ImportPreview;
      this.setData({ preview });
    } catch (error) { wx.showToast({ title: errorMessage(error), icon: "none" }); }
    finally { this.setData({ previewing: false }); }
  },

  async commitImport() {
    if (!this.data.preview || this.data.preview.errors.length > 0 || this.data.committing) {
      wx.showToast({ title: "请先修正预览错误", icon: "none" });
      return;
    }
    const kind = this.data.kindOptions[this.data.kindIndex]?.value;
    this.setData({ committing: true });
    try {
      const result = await uploadImportFile(kind, "commit", this.data.filePath, this.data.overwrite) as ImportResult;
      this.setData({ result, preview: null, fileName: "", filePath: "" });
      wx.showToast({ title: "导入完成", icon: "success" });
    } catch (error) { wx.showToast({ title: errorMessage(error), icon: "none" }); }
    finally { this.setData({ committing: false }); }
  },

  goBack() { wx.navigateBack(); }
});