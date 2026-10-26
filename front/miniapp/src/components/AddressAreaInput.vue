<template>
  <view class="wg-addr-field">
    <input
      class="wg-input"
      :maxlength="maxlength"
      :placeholder="placeholder"
      :focus="focus"
      confirm-type="search"
      :value="value"
      @input="onInput"
      @confirm="onConfirm"
    />
    <view v-if="searching" class="wg-label" style="margin-top: 6px">正在搜索…</view>
    <scroll-view
      v-if="items.length"
      scroll-y
      class="wg-addr-suggest"
      :style="items.length > 4 ? 'height: 236px' : ''"
    >
      <view v-for="(item, index) in items" :key="'addr-' + index" class="wg-addr-suggest-item" @click="choose(item)">
        <text class="wg-addr-suggest-name">{{ item.name }}</text>
        <text class="wg-label">{{ item.area || item.address }}</text>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import { searchPois, suggestPois } from '@/api/geo'

/**
 * 4D-补3：小区/区域输入 + 内联联想。
 * 输入 300ms 防抖拉输入提示，中文 1 字、英文 2 字起；键盘“搜索”仍可主动查一次。
 * 选中候选项时回传名称并抛出 select；手动改字由父组件清空旧坐标。
 */
const DEBOUNCE_MS = 300
const MAX_ITEMS = 8

export default {
  props: {
    value: {
      type: String,
      default: ''
    },
    placeholder: {
      type: String,
      default: '小区/区域'
    },
    maxlength: {
      type: Number,
      default: 100
    },
    /** 4D-补5：从「补全地图位置」进入时自动聚焦 */
    focus: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      items: [],
      searching: false,
      timer: null,
      seq: 0
    }
  },
  beforeUnmount() {
    this.clearTimer()
  },
  methods: {
    onInput(event) {
      const value = event.detail.value
      this.$emit('input', value)
      this.items = []
      this.scheduleTips(value)
    },
    scheduleTips(value) {
      this.clearTimer()
      this.seq += 1
      const keyword = (value || '').trim()
      if (!this.shouldSuggest(keyword)) {
        return
      }
      this.timer = setTimeout(() => this.loadTips(keyword), DEBOUNCE_MS)
    },
    async loadTips(keyword) {
      const seq = ++this.seq
      try {
        const list = await suggestPois(keyword)
        if (seq !== this.seq) {
          return
        }
        this.items = (list || []).slice(0, MAX_ITEMS)
      } catch (e) {
        // 联想失败静默，用户可以继续手填或按搜索键
        if (seq === this.seq) {
          this.items = []
        }
      }
    },
    /** 中文单字也有意义（家/苑），英文至少 2 个字符再联想 */
    shouldSuggest(keyword) {
      if (!keyword) {
        return false
      }
      return keyword.length >= 2 || /[\u4e00-\u9fa5]/.test(keyword)
    },
    async onConfirm() {
      this.clearTimer()
      this.seq += 1
      const keyword = (this.value || '').trim()
      if (!keyword) {
        return
      }
      this.searching = true
      try {
        const list = await searchPois(keyword)
        this.items = (list || []).slice(0, MAX_ITEMS)
      } catch (e) {
        console.error('[微光] 地址搜索失败', e)
        uni.showToast({ title: '搜索暂不可用，请手动填写', icon: 'none' })
        this.items = []
      } finally {
        this.searching = false
      }
    },
    choose(item) {
      const hasGeo = item && item.latitude !== null && item.latitude !== undefined
        && item.longitude !== null && item.longitude !== undefined
      this.items = []
      this.$emit('input', item.name || item.address || this.value)
      if (hasGeo) {
        this.$emit('select', item)
      }
    },
    clearTimer() {
      if (this.timer) {
        clearTimeout(this.timer)
        this.timer = null
      }
    }
  }
}
</script>

<style lang="scss">
.wg-addr-field {
  min-width: 100%;
}

.wg-addr-suggest {
  margin-top: 6px;
  border: 1px solid var(--wg-line);
  border-radius: 10px;
  background-color: var(--wg-surface);
  overflow: hidden;
}

.wg-addr-suggest-item {
  padding: 10px 12px;
  border-bottom: 1px dashed #e7e2d8;
}

.wg-addr-suggest-item:last-child {
  border-bottom: none;
}

.wg-addr-suggest-name {
  display: block;
  font-size: var(--wg-font-base);
  color: var(--wg-text);
}
</style>
