import type { Season } from "../../types/domain";

type SeasonOption = { label: string; value: number };

Component({
  properties: {
    seasons: { type: Array, value: [] as Season[] },
    selectedId: { type: Number, value: 0 }
  },
  data: {
    visible: false,
    selectedLabel: "选择赛季",
    pickerValue: [] as number[],
    options: [] as SeasonOption[]
  },
  observers: {
    "seasons, selectedId"(seasons: Season[], selectedId: number) {
      const selected = seasons.find((season) => season.id === selectedId);
      this.setData({
        selectedLabel: selected?.name ?? "选择赛季",
        pickerValue: selectedId ? [selectedId] : [],
        options: seasons.map((season) => ({ label: season.name, value: season.id }))
      });
    }
  },
  methods: {
    openPicker() {
      this.setData({ visible: true });
    },
    closePicker() {
      this.setData({ visible: false });
    },
    onConfirm(event: WechatMiniprogram.CustomEvent<{ value?: Array<string | number> }>) {
      const value = event.detail?.value?.[0];
      this.setData({ visible: false });
      if (value !== undefined && value !== null) {
        this.triggerEvent("change", { seasonId: Number(value) });
      }
    }
  }
});
