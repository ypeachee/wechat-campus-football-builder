import { setUnauthorizedHandler } from "./services/http";

App({
  onLaunch() {
    setUnauthorizedHandler(() => {
      wx.showToast({ title: "登录已过期，请重新登录", icon: "none", duration: 1800 });
      wx.switchTab({ url: "/pages/profile/index" });
    });
  }
});
