<template>
  <view class="wg-image-root" :style="rootStyle">
    <image v-if="resolved" :src="resolved" :mode="mode" :style="innerStyle" />
    <view v-else class="wg-image-empty" :style="emptyStyle" />
  </view>
</template>

<script>
import { resolveLocalImage } from '@/utils/image'

/**
 * 4D-补4/5：远程图片兼容组件。
 * HTTP 图片在微信开发者工具中会被拒绝，这里统一走 utils/image 解析成本地路径或 base64。
 * 尺寸显式传给容器（小程序组件 class 落在宿主节点上，不能直接作用到内部 image）。
 * fluid=true 用于聊天气泡这类"宽度由外层决定、高度自适应"的图片。
 */
export default {
  props: {
    src: {
      type: String,
      default: ''
    },
    mode: {
      type: String,
      default: 'aspectFill'
    },
    width: {
      type: Number,
      default: 48
    },
    height: {
      type: Number,
      default: 48
    },
    radius: {
      type: String,
      default: '50%'
    },
    fluid: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      resolved: '',
      seq: 0
    }
  },
  computed: {
    rootStyle() {
      if (this.fluid) {
        return 'display:block;'
      }
      return `width:${this.width}px;height:${this.height}px;border-radius:${this.radius};overflow:hidden;display:block;`
    },
    innerStyle() {
      if (this.fluid) {
        return 'width:100%;height:auto;display:block;'
      }
      return 'width:100%;height:100%;display:block;'
    },
    emptyStyle() {
      if (this.fluid) {
        return 'width:100%;min-height:96px;display:block;'
      }
      return 'width:100%;height:100%;display:block;'
    }
  },
  watch: {
    src: {
      immediate: true,
      handler(value) {
        this.resolve(value)
      }
    }
  },
  methods: {
    async resolve(raw) {
      const seq = ++this.seq
      const src = await resolveLocalImage(raw)
      if (seq !== this.seq) {
        return
      }
      this.resolved = src
    }
  }
}
</script>

<style lang="scss">
.wg-image-root {
  flex-shrink: 0;
}

.wg-image-empty {
  background-color: var(--wg-surface3);
}
</style>
