Component({
  properties: {
    value: { type: Number, value: 0 },
    readonly: { type: Boolean, value: false },
    disabled: { type: Boolean, value: false }
  },
  data: { displayStars: 0 },
  observers: {
    value(value: number) {
      this.setData({ displayStars: Math.max(0, Math.min(5, Math.round(value || 0))) });
    }
  },
  lifetimes: {
    attached() {
      this.setData({ displayStars: Math.max(0, Math.min(5, Math.round(this.data.value || 0))) });
    }
  },
  methods: {
    select(event: WechatMiniprogram.BaseEvent) {
      if (this.data.readonly || this.data.disabled) return;
      const stars = Number(event.currentTarget.dataset.value);
      this.setData({ displayStars: stars });
      this.triggerEvent("change", { stars });
    }
  }
});
