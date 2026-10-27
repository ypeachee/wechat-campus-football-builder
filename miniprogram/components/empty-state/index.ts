Component({
  properties: {
    title: { type: String, value: "暂无数据" },
    description: { type: String, value: "稍后再来看看" },
    actionText: { type: String, value: "" }
  },
  methods: {
    handleAction() {
      this.triggerEvent("action");
    }
  }
});
