<template>
  <view class="wg-page" :class="userStore.rootClass">
    <!-- A1 第 1 步：选模板 / 自由描述 / 再来一次 -->
    <view class="wg-card">
      <text class="wg-title">想请邻居帮什么忙？</text>
      <text class="wg-label">选一个模板，或者用自己的话说说</text>
    </view>
    <view class="wg-grid">
      <view
        v-for="(tpl, index) in templates"
        :key="tpl.id"
        class="wg-grid-item"
        :style="gridTint(index)"
        @click="chooseTemplate(tpl)"
      >
        <view class="wg-grid-icon">
          <u-icon :name="tplIcon(tpl)" :size="27" :color="gridTint(index).color" />
        </view>
        <text class="wg-grid-name">{{ tpl.name }}</text>
        <text class="wg-grid-desc">{{ tpl.coverDesc }}</text>
      </view>
    </view>
    <view v-if="templatesFailed" class="wg-card">
      <text class="wg-label">模板加载失败，请检查网络后重试</text>
      <view class="wg-btn wg-btn-plain" style="margin-top: 12px" @click="loadTemplates">重新加载</view>
    </view>
    <view class="wg-btn" @click="startFree">自由描述（智能小助手帮我整理）</view>
    <view class="wg-btn wg-btn-plain" style="margin-top: 12px" @click="loadRepeatOrders">再来一次（选一条历史求助）</view>
    <view v-if="repeatOrders.length > 0" class="wg-card" style="margin-top: 12px">
      <view v-for="order in repeatOrders" :key="order.id" class="wg-repeat-item" @click="chooseRepeat(order)">
        {{ order.title }}（{{ order.createdAt }}）
      </view>
    </view>

    <!-- 底部常驻紧急提示条 -->
    <view class="wg-notice wg-notice-warn">
      <u-icon name="warning" :size="18" />
      <text class="wg-emergency-text">{{ emergencyTip }}</text>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { listMyOrders, listTemplates } from '@/api/order'
import { EMERGENCY_TIP, STORAGE_KEYS } from '@/utils/constants'

/* 8 宫格视觉层（五色低饱和轮流 + 模板图标映射，不改业务逻辑） */
const GRID_TINTS = [
  { backgroundColor: '#d9e2d6', color: '#4f6b4a' },
  { backgroundColor: '#dbe3e7', color: '#4e6b78' },
  { backgroundColor: '#ead9d3', color: '#8a5f52' },
  { backgroundColor: '#ede3d0', color: '#8a7648' },
  { backgroundColor: '#e4deec', color: '#63568c' }
]
const TPL_ICONS = {
  跑腿: 'search',
  小修小补: 'setting',
  家务搭把手: 'reload',
  陪伴同行: 'heart-fill',
  教学: 'phone',
  网上办事: 'file-text',
  花草宠物: 'star-fill',
  其他小事: 'grid'
}

export default {
  data() {
    return {
      templates: [],
      templatesFailed: false,
      repeatOrders: [],
      emergencyTip: EMERGENCY_TIP
    }
  },
  computed: {
    userStore() {
      return useUserStore()
    }
  },
  onLoad() {
    this.loadTemplates()
  },
  methods: {
    gridTint(index) {
      return GRID_TINTS[index % GRID_TINTS.length]
    },
    tplIcon(tpl) {
      return TPL_ICONS[tpl.name] || 'grid'
    },
    async loadTemplates() {
      this.templatesFailed = false
      try {
        this.templates = await listTemplates()
      } catch (e) {
        console.error('[微光] 模板加载失败', e)
        this.templatesFailed = true
      }
    },
    async loadRepeatOrders() {
      // 「再来一次」= 选一条历史求助（已完成/已取消/已超时均可），不再只限 EXPIRED
      const result = await listMyOrders({ tab: 'DONE', page: 1, pageSize: 50 })
      this.repeatOrders = result.records || []
      if (this.repeatOrders.length === 0) {
        uni.showToast({ title: '还没有历史求助', icon: 'none' })
      }
    },
    /** A1：跨页草稿（选出模板/历史单后写入 storage，交给下一页表单预填） */
    toForm(draft) {
      uni.setStorageSync(STORAGE_KEYS.PUBLISH_DRAFT, draft)
      uni.navigateTo({ url: '/pages/order/publish-form' })
    },
    chooseTemplate(tpl) {
      this.toForm({
        entry: 'template',
        aiResult: null,
        form: {
          source: 'TEMPLATE',
          orderType: tpl.defaultType,
          title: tpl.title,
          description: '',
          addressId: null,
          tags: [],
          timeExpect: 'ANY',
          urgentFlag: 0,
          repeatFromOrderId: null
        }
      })
    },
    startFree() {
      uni.setStorageSync(STORAGE_KEYS.PUBLISH_DRAFT, null)
      uni.navigateTo({ url: '/pages/order/publish-free' })
    },
    chooseRepeat(order) {
      this.toForm({
        entry: 'repeat',
        aiResult: null,
        form: {
          source: 'REPEAT',
          orderType: order.orderType,
          title: order.title,
          description: '',
          addressId: null,
          tags: (order.tags || []).slice(0, 3),
          timeExpect: 'ANY',
          urgentFlag: 0,
          repeatFromOrderId: order.id
        }
      })
    }
  }
}
</script>

<style lang="scss">
.wg-grid {
  display: flex;
  flex-wrap: wrap;
}

.wg-grid-item {
  width: 46%;
  margin: 0 2% 12px 2%;
  padding: var(--wg-space);
  border-radius: var(--wg-radius);
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  font-size: var(--wg-font-base);
}

.wg-grid-icon {
  align-self: flex-start;
  margin-bottom: 6px;
}

.wg-grid-name {
  font-size: var(--wg-font-base);
  font-weight: 800;
}

.wg-grid-desc {
  font-size: var(--wg-font-sm);
  line-height: 1.5;
  opacity: 0.85;
  margin-top: 2px;
}

.wg-repeat-item {
  padding: 12px 0;
  border-bottom: 1px solid var(--wg-line);
  font-size: var(--wg-font-base);
}

.wg-repeat-item:last-child {
  border-bottom: none;
}

.wg-emergency-text {
  margin-left: 8px;
  flex: 1;
}
</style>
