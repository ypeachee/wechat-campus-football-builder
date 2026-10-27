import { setUnauthorizedHandler } from "./services/http";

App({
  onLaunch() {
    setUnauthorizedHandler(() => {
      wx.switchTab({ url: "/pages/profile/index" });
    });
  }
});
